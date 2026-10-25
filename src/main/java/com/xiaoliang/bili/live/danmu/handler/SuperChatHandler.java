package com.xiaoliang.bili.live.danmu.handler;

import com.xiaoliang.bili.live.danmu.Message;
import com.xiaoliang.bili.live.danmu.MessageHandler;
import com.xiaoliang.bili.live.danmu.User;
import com.xiaoliang.bili.live.danmu.model.SuperChat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class SuperChatHandler implements MessageHandler {
    static final Logger LOGGER = LoggerFactory.getLogger(SuperChatHandler.class);
    private final Consumer<SuperChat> onSuperChat;

    public SuperChatHandler(Consumer<SuperChat> onSuperChat) {
        this.onSuperChat = onSuperChat;
    }

    @Override
    public boolean canHandle(@NotNull Message message) {
        return message.cmd != null && message.cmd.startsWith("SUPER_CHAT_MESSAGE");
    }

    @Override
    public void handle(Message message) {
        try {
            JsonElement data = message.data;
            if (data == null || !data.isJsonObject()) {
                LOGGER.warn("醒目留言包中没有data");
                return;
            }
            JsonObject dataObj = data.getAsJsonObject();
            
            var sc = new SuperChat();
            sc.user.uid = text(dataObj, "uid");
            JsonObject userInfo = dataObj.has("user_info") && dataObj.get("user_info").isJsonObject()
                    ? dataObj.getAsJsonObject("user_info") : null;
            sc.user.name = userInfo == null ? "" : text(userInfo, "uname");
            sc.user.guardLevel = userInfo == null ? 0 : number(userInfo, "guard_level");

            JsonElement fansMedal = dataObj.get("medal_info");
            if (fansMedal != null && fansMedal.isJsonObject()) {
                JsonObject medalObj = fansMedal.getAsJsonObject();
                String medalName = text(medalObj, "medal_name");
                if (!medalName.isEmpty()) {
                    sc.user.fansMedal = new User.FansMedal();
                    sc.user.fansMedal.name = medalName;
                    sc.user.fansMedal.level = number(medalObj, "medal_level");
                }
            }

            sc.id = number(dataObj, "id");
            sc.body = text(dataObj, "message");
            sc.price = number(dataObj, "price");
            sc.time = number(dataObj, "time");
            if (sc.time <= 0) {
                sc.time = 30;
            }
            onSuperChat.accept(sc);
        } catch (Exception ex) {
            LOGGER.error("解析消息出错", ex);
        }
    }

    private static String text(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.getAsString();
    }

    private static int number(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return 0;
        }
        try {
            return element.getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }
}
