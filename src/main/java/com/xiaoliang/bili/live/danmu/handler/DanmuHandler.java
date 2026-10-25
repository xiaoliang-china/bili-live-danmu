package com.xiaoliang.bili.live.danmu.handler;

import com.xiaoliang.bili.live.danmu.Message;
import com.xiaoliang.bili.live.danmu.MessageHandler;
import com.xiaoliang.bili.live.danmu.User;
import com.xiaoliang.bili.live.danmu.model.Danmu;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class DanmuHandler implements MessageHandler {
    static final Logger LOGGER = LoggerFactory.getLogger(DanmuHandler.class);
    private final Consumer<Danmu> onDanmu;

    public DanmuHandler(Consumer<Danmu> onDanmu) {
        this.onDanmu = onDanmu;
    }

    @Override
    public boolean canHandle(Message message) {
        if (message.cmd == null || !message.cmd.startsWith("DANMU_MSG"))
            return false;
        if (message.info == null || !message.info.isJsonArray())
            return false;
        JsonArray info = message.info.getAsJsonArray();
        return !isEmoji(info);
    }

    static boolean isEmoji(JsonArray info) {
        if (info == null || info.isEmpty() || !info.get(0).isJsonArray()) {
            return false;
        }
        JsonArray head = info.get(0).getAsJsonArray();
        if (head.size() <= 12 || head.get(12).isJsonNull()) {
            return false;
        }
        try {
            return head.get(12).getAsInt() == 1;
        } catch (Exception e) {
            return false;
        }
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
            var danmu = new Danmu();
            JsonArray user = infoArray.get(2).getAsJsonArray();
            danmu.user.uid = user.get(0).isJsonNull() ? "" : user.get(0).getAsString();
            danmu.user.name = user.size() > 1 && !user.get(1).isJsonNull() ? user.get(1).getAsString() : "";
            danmu.user.guardLevel = infoArray.size() > 7 && infoArray.get(7).isJsonPrimitive()
                    ? infoArray.get(7).getAsInt() : 0;

            //解析粉丝团
            JsonElement fansMedal = infoArray.get(3);
            if (fansMedal != null && fansMedal.isJsonArray() && fansMedal.getAsJsonArray().size() >= 2) {
                danmu.user.fansMedal = new User.FansMedal();
                danmu.user.fansMedal.level = fansMedal.getAsJsonArray().get(0).getAsInt();
                danmu.user.fansMedal.name = fansMedal.getAsJsonArray().get(1).getAsString();
            }

            //解析内容
            danmu.body = infoArray.get(1).getAsString();
            onDanmu.accept(danmu);
        } catch (Exception ex) {
            LOGGER.error("解析弹幕包出错", ex);
        }
    }
}
