package com.xiaoliang.bili.live.danmu.handler;

import com.google.gson.Gson;
import com.xiaoliang.bili.live.danmu.Message;
import com.xiaoliang.bili.live.danmu.model.Guard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardHandlerTest {
    private final Gson gson = new Gson();
    private List<Guard> received;
    private GuardHandler handler;

    @BeforeEach
    void setUp() {
        received = new ArrayList<>();
        handler = new GuardHandler(received::add);
    }

    @Test
    void testUserToastPriceIsGoldCoin() {
        String json = "{\"cmd\":\"USER_TOAST_MSG\",\"data\":{\"uid\":1,\"username\":\"舰长\",\"guard_level\":3,\"gift_id\":10003,\"role_name\":\"舰长\",\"price\":138000,\"num\":1,\"unit\":\"月\"}}";

        Message message = gson.fromJson(json, Message.class);
        assertTrue(handler.canHandle(message));
        handler.handle(message);

        Guard guard = received.get(0);
        assertEquals("舰长", guard.name);
        assertEquals(138f, guard.price, 0.001f);
        assertEquals("月", guard.unit);
    }

    @Test
    void testGuardBuyPriceIsYuan() {
        String json = "{\"cmd\":\"GUARD_BUY\",\"data\":{\"uid\":2,\"username\":\"总督\",\"guard_level\":1,\"gift_id\":10001,\"gift_name\":\"总督\",\"price\":19998,\"num\":1}}";

        Message message = gson.fromJson(json, Message.class);
        handler.handle(message);

        Guard guard = received.get(0);
        assertEquals("总督", guard.name);
        assertEquals(19998f, guard.price, 0.001f);
        assertEquals(1, guard.num);
        assertEquals("月", guard.unit);
    }
}
