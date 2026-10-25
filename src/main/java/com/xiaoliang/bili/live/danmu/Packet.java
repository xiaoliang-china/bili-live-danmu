package com.xiaoliang.bili.live.danmu;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.zip.InflaterInputStream;

public class Packet {
    static final Logger LOGGER = LoggerFactory.getLogger(Packet.class);
    public static final short HEADER_LENGTH = 16;
    public static final int SEQUENCE_ID = 0;
    public final byte[] body;
    public final Operation operation;

    /**
     * 0 未压缩，1 心跳人气（Int32），2 zlib 压缩，3 brotli 压缩。
     * 本库发出的心跳和鉴权包不压缩。
     */
    public final short version = 0;

    public int length() {
        return body.length + HEADER_LENGTH;
    }

    public Packet(Operation operation, byte[] body) {
        this.operation = operation;
        this.body = body;
    }

    public ByteBuffer pack() {
        int length = length();
        var buffer = ByteBuffer.allocate(length);

        buffer.putInt(length);
        buffer.putShort(HEADER_LENGTH);
        buffer.putShort(version);
        buffer.putInt(operation.code);
        buffer.putInt(SEQUENCE_ID);
        buffer.put(body);

        buffer.position(0);
        return buffer;
    }

    public static @NotNull ArrayList<Packet> unPack(@NotNull ByteBuffer buffer) {
        var packs = new ArrayList<Packet>();
        int offset = 0;
        int len = buffer.limit();
        while (offset + HEADER_LENGTH <= len) {
            int bodyLen = buffer.getInt(offset) - HEADER_LENGTH;
            if (bodyLen < 0 || offset + HEADER_LENGTH + bodyLen > len) {
                LOGGER.error("弹幕包长度非法: offset={} bodyLen={} limit={}", offset, bodyLen, len);
                break;
            }
            try {
                short ver = buffer.getShort(offset + 6);
                Operation op = Operation.parse(buffer.getInt(offset + 8));
                byte[] body = new byte[bodyLen];
                buffer.get(offset + HEADER_LENGTH, body, 0, bodyLen);

                // 2 是 zlib，3 是 brotli。压缩包里是下一批完整协议包，需要再解一次。
                if (ver == 2 || ver == 3) {
                    try {
                        body = ver == 3 ? decompressBrotli(body) : decompress(body);
                        packs.addAll(unPack(ByteBuffer.wrap(body)));
                    } catch (IOException | UnsatisfiedLinkError | NoClassDefFoundError e) {
                        LOGGER.error("解压失败 ver={}", ver, e);
                    }
                    continue;
                }
                if (op == null) {
                    continue;
                }

                packs.add(new Packet(op, body));
            } finally {
                offset += bodyLen + HEADER_LENGTH;
            }
        }
        return packs;
    }

    public static byte @NotNull [] decompress(byte @NotNull [] data) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        InflaterInputStream iis = new InflaterInputStream(inputStream);
        return iis.readAllBytes();
    }

    /**
     * protover=3 的包体是 brotli。org.brotli:dec 在运行时存在时使用，
     * 缺失时抛出 IOException，调用方跳过这一包，不影响 zlib 包。
     */
    static byte @NotNull [] decompressBrotli(byte @NotNull [] data) throws IOException {
        try {
            Class<?> decoderClass = Class.forName("org.brotli.dec.BrotliInputStream");
            try (var input = new ByteArrayInputStream(data);
                 var brotli = (java.io.InputStream) decoderClass
                         .getConstructor(java.io.InputStream.class)
                         .newInstance(input);
                 var output = new ByteArrayOutputStream()) {
                brotli.transferTo(output);
                return output.toByteArray();
            }
        } catch (IOException e) {
            throw e;
        } catch (ReflectiveOperationException e) {
            throw new IOException("brotli 解码不可用", e);
        }
    }

    public enum Operation {
        /**
         * 客户端发送的心跳包(30秒发送一次)
         */
        HEARTBEAT(2),
        /**
         * 服务器收到心跳包的回复
         */
        HEARTBEAT_REPLY(3),
        /**
         * 服务器推送的弹幕消息包
         */
        SEND_SMS_REPLY(5),
        /**
         * 客户端发送的鉴权包(客户端发送的第一个包)
         */
        AUTH(7),
        /**
         * 服务器收到鉴权包后的回复
         */
        AUTH_REPLY(8);
        public final int code;

        Operation(int code) {
            this.code = code;
        }

        public static Operation parse(int code) {
            return switch (code) {
                case 2 -> Operation.HEARTBEAT;
                case 3 -> Operation.HEARTBEAT_REPLY;
                case 5 -> Operation.SEND_SMS_REPLY;
                case 7 -> Operation.AUTH;
                case 8 -> Operation.AUTH_REPLY;
                default -> null;
            };
        }
    }
}
