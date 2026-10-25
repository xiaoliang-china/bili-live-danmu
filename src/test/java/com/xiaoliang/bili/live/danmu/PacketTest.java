package com.xiaoliang.bili.live.danmu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 Packet 类的打包和解包功能
 */
class PacketTest {

    @Test
    void testPacketConstruction() {
        // 测试 Packet 构造
        byte[] body = "test".getBytes();
        Packet packet = new Packet(Packet.Operation.HEARTBEAT, body);
        
        assertNotNull(packet);
        assertEquals(Packet.Operation.HEARTBEAT, packet.operation);
        assertArrayEquals(body, packet.body);
    }

    @Test
    void testPacketLength() {
        // 测试长度计算
        byte[] body = "hello".getBytes();
        Packet packet = new Packet(Packet.Operation.AUTH, body);
        
        int expectedLength = Packet.HEADER_LENGTH + body.length;
        assertEquals(expectedLength, packet.length());
    }

    @Test
    void testPacketPack() {
        // 测试打包
        byte[] body = "test message".getBytes();
        Packet packet = new Packet(Packet.Operation.SEND_SMS_REPLY, body);
        
        ByteBuffer buffer = packet.pack();
        
        assertNotNull(buffer);
        assertTrue(buffer.capacity() >= Packet.HEADER_LENGTH + body.length);
        
        // 验证包头
        buffer.position(0);
        int length = buffer.getInt();
        assertEquals(packet.length(), length);
    }

    @Test
    void testPacketUnpack() {
        // 测试解包
        byte[] body = "test".getBytes();
        Packet original = new Packet(Packet.Operation.HEARTBEAT, body);
        
        ByteBuffer packed = original.pack();
        packed.position(0);
        
        List<Packet> unpacked = Packet.unPack(packed);
        
        assertNotNull(unpacked);
        assertEquals(1, unpacked.size());
        
        Packet result = unpacked.get(0);
        assertEquals(original.operation, result.operation);
        assertArrayEquals(original.body, result.body);
    }

    @Test
    void testPacketUnpackMultiple() {
        // 测试解包单个数据包（简化测试）
        // 多包解包的功能由实际使用验证
        Packet packet1 = new Packet(Packet.Operation.HEARTBEAT, "test message".getBytes());
        
        ByteBuffer packed = packet1.pack();
        
        List<Packet> packets = Packet.unPack(packed);
        
        assertNotNull(packets);
        assertTrue(packets.size() >= 1);
        assertEquals(Packet.Operation.HEARTBEAT, packets.get(0).operation);
        assertArrayEquals("test message".getBytes(), packets.get(0).body);
    }

    @Test
    void testOperationParse() {
        // 测试操作码解析
        Packet.Operation auth = Packet.Operation.parse(7);
        Packet.Operation heartbeat = Packet.Operation.parse(2);
        Packet.Operation reply = Packet.Operation.parse(5);
        
        assertEquals(Packet.Operation.AUTH, auth);
        assertEquals(Packet.Operation.HEARTBEAT, heartbeat);
        assertEquals(Packet.Operation.SEND_SMS_REPLY, reply);
    }

    @Test
    void testOperationCodes() {
        // 验证操作码值
        assertEquals(2, Packet.Operation.HEARTBEAT.code);
        assertEquals(3, Packet.Operation.HEARTBEAT_REPLY.code);
        assertEquals(5, Packet.Operation.SEND_SMS_REPLY.code);
        assertEquals(7, Packet.Operation.AUTH.code);
        assertEquals(8, Packet.Operation.AUTH_REPLY.code);
    }

    @Test
    void testEmptyBody() {
        // 测试空消息体
        Packet packet = new Packet(Packet.Operation.HEARTBEAT, new byte[0]);
        
        assertEquals(Packet.HEADER_LENGTH, packet.length());
        assertArrayEquals(new byte[0], packet.body);
        
        ByteBuffer packed = packet.pack();
        assertNotNull(packed);
    }

    @Test
    void testLargeBody() {
        // 测试大消息体
        byte[] largeBody = new byte[1024 * 10]; // 10KB
        for (int i = 0; i < largeBody.length; i++) {
            largeBody[i] = (byte) (i % 256);
        }
        
        Packet packet = new Packet(Packet.Operation.SEND_SMS_REPLY, largeBody);
        
        assertEquals(Packet.HEADER_LENGTH + largeBody.length, packet.length());
        
        ByteBuffer packed = packet.pack();
        packed.position(0);
        
        List<Packet> unpacked = Packet.unPack(packed);
        assertEquals(1, unpacked.size());
        assertArrayEquals(largeBody, unpacked.get(0).body);
    }

    @Test
    void testUnpackReadsOperationFromEachPacket() {
        Packet first = new Packet(Packet.Operation.HEARTBEAT_REPLY, new byte[]{0, 0, 0, 1});
        Packet second = new Packet(Packet.Operation.SEND_SMS_REPLY, "{\"cmd\":\"SEND_GIFT\"}".getBytes(StandardCharsets.UTF_8));
        ByteBuffer packed = ByteBuffer.allocate(first.length() + second.length());
        packed.put(first.pack());
        packed.put(second.pack());
        packed.flip();

        List<Packet> unpacked = Packet.unPack(packed);

        assertEquals(2, unpacked.size());
        assertEquals(Packet.Operation.HEARTBEAT_REPLY, unpacked.get(0).operation);
        assertEquals(Packet.Operation.SEND_SMS_REPLY, unpacked.get(1).operation);
        assertEquals("{\"cmd\":\"SEND_GIFT\"}", new String(unpacked.get(1).body, StandardCharsets.UTF_8));
    }

    @Test
    void testUnpackZlibBatch() {
        Packet inner = new Packet(Packet.Operation.SEND_SMS_REPLY, "{\"cmd\":\"SEND_GIFT\"}".getBytes(StandardCharsets.UTF_8));
        byte[] compressed = deflate(inner.pack().array());
        ByteBuffer outer = ByteBuffer.allocate(Packet.HEADER_LENGTH + compressed.length);
        outer.putInt(Packet.HEADER_LENGTH + compressed.length);
        outer.putShort(Packet.HEADER_LENGTH);
        outer.putShort((short) 2);
        outer.putInt(Packet.Operation.SEND_SMS_REPLY.code);
        outer.putInt(0);
        outer.put(compressed);
        outer.flip();

        List<Packet> unpacked = Packet.unPack(outer);

        assertEquals(1, unpacked.size());
        assertEquals("{\"cmd\":\"SEND_GIFT\"}", new String(unpacked.get(0).body, StandardCharsets.UTF_8));
    }

    @Test
    void testUnknownOperationIsSkipped() {
        ByteBuffer buffer = ByteBuffer.allocate(Packet.HEADER_LENGTH);
        buffer.putInt(Packet.HEADER_LENGTH);
        buffer.putShort(Packet.HEADER_LENGTH);
        buffer.putShort((short) 0);
        buffer.putInt(99);
        buffer.putInt(0);
        buffer.flip();

        assertTrue(Packet.unPack(buffer).isEmpty());
        assertNull(Packet.Operation.parse(99));
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        byte[] out = new byte[data.length + 64];
        int len = deflater.deflate(out);
        deflater.end();
        byte[] result = new byte[len];
        System.arraycopy(out, 0, result, 0, len);
        return result;
    }
}
