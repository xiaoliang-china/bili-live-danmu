package com.xiaoliang.bili.live.danmu.handler;

import com.xiaoliang.bili.live.danmu.Message;
import com.xiaoliang.bili.live.danmu.MessageHandler;
import com.xiaoliang.bili.live.danmu.User;
import com.xiaoliang.bili.live.danmu.model.Emoji;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class EomjiHandler implements MessageHandler {
    static final Logger LOGGER = LoggerFactory.getLogger(EomjiHandler.class);
    private final Consumer<Emoji> onEmoji;

    public EomjiHandler(Consumer<Emoji> onEmoji) {
        this.onEmoji = onEmoji;
    }

    @Override
    public boolean canHandle(Message message) {
        if (message.cmd == null || !message.cmd.startsWith("DANMU_MSG"))
            return false;
        if (message.info == null || !message.info.isJsonArray())
            return false;
        return DanmuHandler.isEmoji(message.info.getAsJsonArray());
    }

    @Override
    public void handle(Message message) {
        try {
            JsonElement info = message.info;
            if (info == null) {
                LOGGER.error("弹幕包中没有info");
                return;
            }
            JsonArray infoArray = info.getAsJsonArray();
            
            //解析用户
            var emoji = new Emoji();
            JsonArray user = infoArray.get(2).getAsJsonArray();
            emoji.user.uid = user.get(0).isJsonNull() ? "" : user.get(0).getAsString();
            emoji.user.name = user.size() > 1 && !user.get(1).isJsonNull() ? user.get(1).getAsString() : "";
            emoji.user.guardLevel = infoArray.size() > 7 && infoArray.get(7).isJsonPrimitive()
                    ? infoArray.get(7).getAsInt() : 0;

            //解析粉丝团
            JsonElement fansMedal = infoArray.get(3);
            if (fansMedal != null && fansMedal.isJsonArray() && fansMedal.getAsJsonArray().size() >= 2) {
                emoji.user.fansMedal = new User.FansMedal();
                emoji.user.fansMedal.level = fansMedal.getAsJsonArray().get(0).getAsInt();
                emoji.user.fansMedal.name = fansMedal.getAsJsonArray().get(1).getAsString();
            }

            //解析内容
            emoji.body = infoArray.get(1).getAsString();
            JsonArray head = infoArray.get(0).getAsJsonArray();
            if (head.size() > 13 && head.get(13).isJsonObject()) {
                JsonElement url = head.get(13).getAsJsonObject().get("url");
                emoji.uri = url == null || url.isJsonNull() ? "" : url.getAsString();
            } else {
                emoji.uri = "";
            }
            onEmoji.accept(emoji);
        } catch (Exception ex) {
            LOGGER.error("解析弹幕包出错", ex);
        }
    }
}
