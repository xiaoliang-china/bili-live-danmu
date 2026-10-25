package com.xiaoliang.bili.live.danmu.handler;

import com.xiaoliang.bili.live.danmu.Message;
import com.xiaoliang.bili.live.danmu.MessageHandler;
import com.xiaoliang.bili.live.danmu.model.Guard;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class GuardHandler implements MessageHandler {
    static final Logger LOGGER = LoggerFactory.getLogger(GuardHandler.class);
    private final Consumer<Guard> onGuard;

    public GuardHandler(Consumer<Guard> onGuard) {
        this.onGuard = onGuard;
    }

    @Override
    public boolean canHandle(@NotNull Message message) {
        // 上船提示有两个命令：旧的 USER_TOAST_MSG，以及 GUARD_BUY。
        return message.cmd != null
                && (message.cmd.startsWith("USER_TOAST_MSG") || message.cmd.startsWith("GUARD_BUY"));
    }

    @Override
    public void handle(Message message) {
        try {
            JsonElement data = message.data;
            if (data == null || !data.isJsonObject()) {
                LOGGER.error("舰长包中没有data");
                return;
            }
            JsonObject dataObj = data.getAsJsonObject();

            var guard = new Guard();
            guard.user.uid = text(dataObj, "uid");
            guard.user.name = text(dataObj, "username");
            if (guard.user.name.isEmpty()) {
                guard.user.name = text(dataObj, "uname");
            }
            guard.user.guardLevel = number(dataObj, "guard_level");
            guard.id = number(dataObj, "gift_id");
            guard.name = text(dataObj, "role_name");
            if (guard.name.isEmpty()) {
                guard.name = text(dataObj, "gift_name");
            }
            int rawPrice = number(dataObj, "price");
            // USER_TOAST_MSG 的 price 是金瓜子，GUARD_BUY 的 price 已经是元。
            guard.price = message.cmd.startsWith("GUARD_BUY") ? rawPrice : rawPrice / 1000f;
            guard.num = number(dataObj, "num");
            if (guard.num <= 0) {
                guard.num = 1;
            }
            guard.level = guard.user.guardLevel;
            guard.unit = text(dataObj, "unit");
            if (guard.unit.isEmpty()) {
                guard.unit = "月";
            }
            onGuard.accept(guard);
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
