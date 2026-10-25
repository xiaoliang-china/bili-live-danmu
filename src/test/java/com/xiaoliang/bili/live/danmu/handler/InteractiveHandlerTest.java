package com.xiaoliang.bili.live.danmu.handler;

import com.google.gson.Gson;
import com.xiaoliang.bili.live.danmu.Message;
import com.xiaoliang.bili.live.danmu.model.Interactive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractiveHandlerTest {
    private final Gson gson = new Gson();
    private List<Interactive> received;
    private InteractiveHandler handler;

    @BeforeEach
    void setUp() {
        received = new ArrayList<>();
        handler = new InteractiveHandler(received::add);
    }

    @Test
    void testLikeClick() {
        String json = "{\"cmd\":\"LIKE_INFO_V3_CLICK\",\"data\":{\"uid\":629518194,\"uname\":\"点赞用户\",\"msg_type\":6,\"like_text\":\"为主播点赞了\",\"fans_medal\":{\"medal_name\":\"德云色\",\"medal_level\":31,\"guard_level\":0}}}";

        Message message = gson.fromJson(json, Message.class);
        assertTrue(handler.canHandle(message));
        handler.handle(message);

        Interactive interactive = received.get(0);
        assertEquals("629518194", interactive.user.uid);
        assertEquals("点赞用户", interactive.user.name);
        assertEquals(Interactive.LIKE, interactive.type);
        assertEquals("为主播点赞了", interactive.text);
        assertEquals("德云色", interactive.user.fansMedal.name);
        assertEquals(31, interactive.user.fansMedal.level);
    }

    @Test
    void testLikeUpdateIsIgnored() {
        String json = "{\"cmd\":\"LIKE_INFO_V3_UPDATE\",\"data\":{\"click_count\":12}}";
        Message message = gson.fromJson(json, Message.class);
        assertFalse(handler.canHandle(message));
    }

    @Test
    void testInteractWordV2Protobuf() {
        String json = "{\"cmd\":\"INTERACT_WORD_V2\",\"data\":{\"dmscore\":3,\"pb\":\"CMqXgNHe7coGEhVpT1PmuLjlrqJfNzM3MjU3MTA3NjciAQEoATDfpL8EOIjg8NYGQM7A7uCWNGIAeJTykNGE+9bwGJoBALIBVwjKl4DR3u3KBhJIChVpT1PmuLjlrqJfNzM3MjU3MTA3NjcSL2h0dHBzOi8vaTAuaGRzbGIuY29tL2Jmcy9mYWNlL21lbWJlci9ub2ZhY2UuanBnIgAyALoBAMIBAA==\"}}";

        Message message = gson.fromJson(json, Message.class);
        assertTrue(handler.canHandle(message));
        handler.handle(message);

        Interactive interactive = received.get(0);
        assertEquals("3706925776636874", interactive.user.uid);
        assertEquals("iOS游客_73725710767", interactive.user.name);
        assertEquals(Interactive.ENTER, interactive.type);
    }
}
