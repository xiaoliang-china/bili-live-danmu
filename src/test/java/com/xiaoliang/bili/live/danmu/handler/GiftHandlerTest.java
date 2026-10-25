package com.xiaoliang.bili.live.danmu.handler;

import com.xiaoliang.bili.live.danmu.Message;
import com.xiaoliang.bili.live.danmu.model.Gift;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 GiftHandler 的 Gson 集成
 */
class GiftHandlerTest {

    private final Gson gson = new Gson();
    private List<Gift> receivedGifts;
    private GiftHandler handler;

    @BeforeEach
    void setUp() {
        receivedGifts = new ArrayList<>();
        handler = new GiftHandler(receivedGifts::add);
    }

    @Test
    void testCanHandleGiftMessage() {
        String json = "{\"cmd\":\"SEND_GIFT\",\"data\":{}}";
        
        Message message = gson.fromJson(json, Message.class);
        
        assertTrue(handler.canHandle(message));
    }

    @Test
    void testHandleGoldGift() {
        // 测试处理金瓜子礼物
        String json = "{\"cmd\":\"SEND_GIFT\",\"data\":{\"uid\":12345,\"uname\":\"送礼用户\",\"giftId\":1,\"giftName\":\"辣条\",\"num\":10,\"total_coin\":10000,\"coin_type\":\"gold\",\"guard_level\":0,\"medal_info\":{\"medal_name\":\"测试勋章\",\"medal_level\":5}}}";
        
        Message message = gson.fromJson(json, Message.class);
        handler.handle(message);
        
        assertEquals(1, receivedGifts.size());
        Gift gift = receivedGifts.get(0);
        
        assertEquals("12345", gift.user.uid);
        assertEquals("送礼用户", gift.user.name);
        assertEquals(1, gift.id);
        assertEquals("辣条", gift.name);
        assertEquals(10, gift.num);
        assertEquals(10.0f, gift.price, 0.001f); // 10000 / 1000 = 10
        assertNotNull(gift.user.fansMedal);
        assertEquals(5, gift.user.fansMedal.level);
        assertEquals("测试勋章", gift.user.fansMedal.name);
    }

    @Test
    void testHandleSilverGift() {
        // 测试处理银瓜子礼物（免费礼物）
        String json = "{\"cmd\":\"SEND_GIFT\",\"data\":{\"uid\":67890,\"uname\":\"用户B\",\"giftId\":2,\"giftName\":\"小心心\",\"num\":5,\"total_coin\":0,\"coin_type\":\"silver\",\"guard_level\":0,\"medal_info\":null}}";
        
        Message message = gson.fromJson(json, Message.class);
        handler.handle(message);
        
        assertEquals(1, receivedGifts.size());
        Gift gift = receivedGifts.get(0);
        
        assertEquals("67890", gift.user.uid);
        assertEquals("用户B", gift.user.name);
        assertEquals(2, gift.id);
        assertEquals("小心心", gift.name);
        assertEquals(5, gift.num);
        assertEquals(0.0f, gift.price, 0.001f); // 银瓜子礼物价格为 0
    }

    @Test
    void testHandleGuardGift() {
        // 测试处理舰长送的礼物
        String json = "{\"cmd\":\"SEND_GIFT\",\"data\":{\"uid\":11111,\"uname\":\"舰长用户\",\"giftId\":3,\"giftName\":\"B坷垃\",\"num\":1,\"total_coin\":9994,\"coin_type\":\"gold\",\"guard_level\":3,\"medal_info\":{\"medal_name\":\"舰队\",\"medal_level\":20}}}";
        
        Message message = gson.fromJson(json, Message.class);
        handler.handle(message);
        
        assertEquals(1, receivedGifts.size());
        Gift gift = receivedGifts.get(0);
        
        assertEquals("舰长用户", gift.user.name);
        assertEquals(3, gift.user.guardLevel);
        assertEquals(9.994f, gift.price, 0.001f);
        assertEquals(20, gift.user.fansMedal.level);
    }

    @Test
    void testHandleGiftWithoutMedal() {
        // 测试处理没有粉丝勋章的礼物
        String json = "{\"cmd\":\"SEND_GIFT\",\"data\":{\"uid\":99999,\"uname\":\"新用户\",\"giftId\":1,\"giftName\":\"辣条\",\"num\":1,\"total_coin\":1000,\"coin_type\":\"gold\",\"guard_level\":0,\"medal_info\":null}}";
        
        Message message = gson.fromJson(json, Message.class);
        handler.handle(message);
        
        assertEquals(1, receivedGifts.size());
        Gift gift = receivedGifts.get(0);
        
        assertEquals("新用户", gift.user.name);
        assertNull(gift.user.fansMedal);
    }

    @Test
    void testHandleSuffixedGiftCommand() {
        String json = "{\"cmd\":\"SEND_GIFT:1911413882\",\"data\":{\"uid\":42,\"uname\":\"后缀礼物\",\"giftId\":9,\"giftName\":\"小花花\",\"num\":2,\"total_coin\":2000,\"coin_type\":\"gold\",\"guard_level\":0,\"medal_info\":null}}";

        Message message = gson.fromJson(json, Message.class);
        assertTrue(handler.canHandle(message));
        handler.handle(message);

        assertEquals(1, receivedGifts.size());
        Gift gift = receivedGifts.get(0);
        assertEquals("小花花", gift.name);
        assertEquals(2.0f, gift.price, 0.001f);
    }

    @Test
    void testHandleComboSend() {
        String json = "{\"cmd\":\"COMBO_SEND\",\"data\":{\"uid\":7,\"uname\":\"连击\",\"gift_id\":3,\"gift_name\":\"牛哇\",\"gift_num\":6,\"combo_total_coin\":6000,\"coin_type\":\"gold\",\"guard_level\":1}}";

        Message message = gson.fromJson(json, Message.class);
        assertTrue(handler.canHandle(message));
        handler.handle(message);

        Gift gift = receivedGifts.get(0);
        assertEquals("牛哇", gift.name);
        assertEquals(6, gift.num);
        assertEquals(6.0f, gift.price, 0.001f);
        assertEquals(1, gift.user.guardLevel);
    }

    @Test
    void testHandleNestedSender() {
        String json = "{\"cmd\":\"SEND_GIFT\",\"data\":{\"giftId\":1,\"giftName\":\"粉丝团灯牌\",\"num\":1,\"price\":1000,\"coin_type\":\"gold\",\"sender_uinfo\":{\"uid\":99,\"base\":{\"name\":\"新协议\"},\"guard\":{\"level\":3},\"medal\":{\"name\":\"灯牌\",\"level\":8}}}}";

        Message message = gson.fromJson(json, Message.class);
        handler.handle(message);

        Gift gift = receivedGifts.get(0);
        assertEquals("99", gift.user.uid);
        assertEquals("新协议", gift.user.name);
        assertEquals(3, gift.user.guardLevel);
        assertEquals("灯牌", gift.user.fansMedal.name);
        assertEquals(8, gift.user.fansMedal.level);
        assertEquals(1.0f, gift.price, 0.001f);
    }

    @Test
    void testHandleGiftV2Protobuf() throws Exception {
        String pb = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/test/resources/gift-v2.pb.txt")).trim();
        String json = "{\"cmd\":\"SEND_GIFT_V2\",\"data\":{\"dmscore\":70,\"pb\":"
                + new Gson().toJson(pb) + "}}";

        Message message = gson.fromJson(json, Message.class);
        assertTrue(handler.canHandle(message));
        handler.handle(message);

        Gift gift = receivedGifts.get(0);
        assertEquals("傲***", gift.user.name);
        assertEquals(31164, gift.id);
        assertEquals("粉丝团灯牌", gift.name);
        assertEquals(1, gift.num);
        assertEquals(0.1f, gift.price, 0.001f);
        assertNotNull(gift.user.fansMedal);
        assertEquals("本天才", gift.user.fansMedal.name);
        assertEquals(2, gift.user.fansMedal.level);
    }

    @Test
    void testCannotHandleOtherMessages() {
        String json = "{\"cmd\":\"DANMU_MSG\",\"info\":[]}";
        
        Message message = gson.fromJson(json, Message.class);
        
        assertFalse(handler.canHandle(message));
    }
}
