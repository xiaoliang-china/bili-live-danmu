package com.xiaoliang.bili.live.danmu.handler;

import com.xiaoliang.bili.live.danmu.Message;
import com.xiaoliang.bili.live.danmu.MessageHandler;
import com.xiaoliang.bili.live.danmu.User;
import com.xiaoliang.bili.live.danmu.model.Gift;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

public class GiftHandler implements MessageHandler {
    static final Logger LOGGER = LoggerFactory.getLogger(GiftHandler.class);
    private final Consumer<Gift> onGift;

    public GiftHandler(Consumer<Gift> onGift) {
        this.onGift = onGift;
    }

    @Override
    public boolean canHandle(Message message) {
        // 新协议会在命令后追加房间号或版本，例如 SEND_GIFT:123、COMBO_SEND:V2。
        return message.cmd != null
                && (message.cmd.startsWith("SEND_GIFT") || message.cmd.startsWith("COMBO_SEND"));
    }

    @Override
    public void handle(Message message) {
        try {
            JsonElement data = message.data;
            if (data == null || !data.isJsonObject()) {
                LOGGER.error("礼物包中没有data");
                return;
            }
            JsonObject dataObj = data.getAsJsonObject();

            var gift = new Gift();
            gift.user.uid = firstString(dataObj, "uid", nestedString(dataObj, "sender_uinfo", "uid"));
            gift.user.name = firstString(dataObj, "uname", nestedString(dataObj, "sender_uinfo", "base", "name"));
            gift.user.guardLevel = firstInt(dataObj, "guard_level",
                    nestedInt(dataObj, "sender_uinfo", "guard", "level"));

            JsonElement fansMedal = firstObject(dataObj, "medal_info", "fans_medal");
            if (fansMedal == null) {
                fansMedal = nested(dataObj, "sender_uinfo", "medal");
            }
            if (fansMedal != null && fansMedal.isJsonObject()) {
                JsonObject medalObj = fansMedal.getAsJsonObject();
                String medalName = firstString(medalObj, "medal_name", firstString(medalObj, "name", ""));
                int medalLevel = firstInt(medalObj, "medal_level", firstInt(medalObj, "level", 0));
                if (!medalName.isEmpty()) {
                    gift.user.fansMedal = new User.FansMedal();
                    gift.user.fansMedal.name = medalName;
                    gift.user.fansMedal.level = medalLevel;
                }
            }

            gift.id = firstInt(dataObj, "giftId", firstInt(dataObj, "gift_id", 0));
            gift.name = firstString(dataObj, "giftName", firstString(dataObj, "gift_name", ""));
            String coinType = firstString(dataObj, "coin_type", firstString(dataObj, "coinType", ""));
            int totalCoin = firstInt(dataObj, "total_coin", firstInt(dataObj, "combo_total_coin", 0));
            if (totalCoin == 0 && "gold".equals(coinType)) {
                totalCoin = firstInt(dataObj, "price", 0) * firstInt(dataObj, "num", 1);
            }
            gift.price = "gold".equals(coinType) ? totalCoin / 1000f : 0;
            gift.num = firstInt(dataObj, "num",
                    firstInt(dataObj, "combo_num", firstInt(dataObj, "gift_num", 0)));
            if (dataObj.has("pb") && !dataObj.get("pb").isJsonNull()) {
                applyGiftPb(gift, dataObj.get("pb").getAsString());
            }
            if (gift.num <= 0) {
                gift.num = 1;
            }
            if (gift.name.isEmpty() && gift.id == 0) {
                return;
            }
            onGift.accept(gift);
        } catch (Exception ex) {
            LOGGER.error("解析消息出错", ex);
        }
    }

    /**
     * SEND_GIFT_V2 把礼物放在 data.pb 里。
     * 外层 field 2 是送礼人，field 10 是礼物（id、名称、数量、金瓜子、货币），
     * field 15 里的 field 3 是粉丝牌。
     */
    private static void applyGiftPb(Gift gift, String pbBase64) {
        if (pbBase64 == null || pbBase64.isBlank()) {
            return;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(pbBase64);
            int i = 0;
            while (i < bytes.length) {
                long key = readVarint(bytes, i);
                int keyLen = varintLength(bytes, i);
                if (keyLen <= 0) {
                    return;
                }
                i += keyLen;
                int field = (int) (key >>> 3);
                int wire = (int) (key & 0x07);
                if (wire == 0) {
                    int valueLen = varintLength(bytes, i);
                    if (valueLen <= 0) {
                        return;
                    }
                    i += valueLen;
                } else if (wire == 2) {
                    long lenLong = readVarint(bytes, i);
                    int lenLen = varintLength(bytes, i);
                    if (lenLen <= 0) {
                        return;
                    }
                    i += lenLen;
                    int len = (int) lenLong;
                    if (len < 0 || i + len > bytes.length) {
                        return;
                    }
                    if (field == 2 && isBlank(gift.user.name)) {
                        gift.user.name = new String(bytes, i, len, StandardCharsets.UTF_8);
                    } else if (field == 10) {
                        applyGiftInfo(gift, bytes, i, i + len);
                    } else if (field == 15 && gift.user.fansMedal == null) {
                        applySenderMedal(gift, bytes, i, i + len);
                    }
                    i += len;
                } else if (wire == 1) {
                    i += 8;
                } else if (wire == 5) {
                    i += 4;
                } else {
                    return;
                }
            }
        } catch (Exception ex) {
            LOGGER.debug("礼物 protobuf 解析失败", ex);
        }
    }

    private static void applyGiftInfo(Gift gift, byte[] data, int start, int end) {
        int num = 0;
        int unitCoin = 0;
        int totalCoin = 0;
        String coinType = "";
        int i = start;
        while (i < end) {
            long key = readVarint(data, i);
            int keyLen = varintLength(data, i);
            if (keyLen <= 0) {
                return;
            }
            i += keyLen;
            int field = (int) (key >>> 3);
            int wire = (int) (key & 0x07);
            if (wire == 0) {
                long value = readVarint(data, i);
                int valueLen = varintLength(data, i);
                if (valueLen <= 0) {
                    return;
                }
                i += valueLen;
                if (field == 1 && gift.id == 0) {
                    gift.id = (int) value;
                } else if (field == 4 && num == 0) {
                    num = (int) value;
                } else if (field == 5) {
                    unitCoin = (int) value;
                } else if (field == 7) {
                    totalCoin = (int) value;
                }
            } else if (wire == 2) {
                long lenLong = readVarint(data, i);
                int lenLen = varintLength(data, i);
                if (lenLen <= 0) {
                    return;
                }
                i += lenLen;
                int len = (int) lenLong;
                if (len < 0 || i + len > end) {
                    return;
                }
                if (field == 2 && isBlank(gift.name)) {
                    gift.name = new String(data, i, len, StandardCharsets.UTF_8);
                } else if (field == 8) {
                    coinType = new String(data, i, len, StandardCharsets.UTF_8);
                }
                i += len;
            } else if (wire == 1) {
                i += 8;
            } else if (wire == 5) {
                i += 4;
            } else {
                return;
            }
        }
        if (gift.num <= 0 && num > 0) {
            gift.num = num;
        }
        if (gift.price <= 0 && "gold".equals(coinType)) {
            int coin = totalCoin > 0 ? totalCoin : unitCoin * Math.max(gift.num, 1);
            gift.price = coin / 1000f;
        }
    }

    private static void applySenderMedal(Gift gift, byte[] data, int start, int end) {
        int i = start;
        while (i < end) {
            long key = readVarint(data, i);
            int keyLen = varintLength(data, i);
            if (keyLen <= 0) {
                return;
            }
            i += keyLen;
            int field = (int) (key >>> 3);
            int wire = (int) (key & 0x07);
            if (wire == 2) {
                long lenLong = readVarint(data, i);
                int lenLen = varintLength(data, i);
                if (lenLen <= 0) {
                    return;
                }
                i += lenLen;
                int len = (int) lenLong;
                if (len < 0 || i + len > end) {
                    return;
                }
                if (field == 3) {
                    readMedal(gift, data, i, i + len);
                }
                i += len;
            } else if (wire == 0) {
                int valueLen = varintLength(data, i);
                if (valueLen <= 0) {
                    return;
                }
                i += valueLen;
            } else if (wire == 1) {
                i += 8;
            } else if (wire == 5) {
                i += 4;
            } else {
                return;
            }
        }
    }

    private static void readMedal(Gift gift, byte[] data, int start, int end) {
        String name = "";
        int level = 0;
        int i = start;
        while (i < end) {
            long key = readVarint(data, i);
            int keyLen = varintLength(data, i);
            if (keyLen <= 0) {
                return;
            }
            i += keyLen;
            int field = (int) (key >>> 3);
            int wire = (int) (key & 0x07);
            if (wire == 0) {
                long value = readVarint(data, i);
                int valueLen = varintLength(data, i);
                if (valueLen <= 0) {
                    return;
                }
                i += valueLen;
                if (field == 2) {
                    level = (int) value;
                }
            } else if (wire == 2) {
                long lenLong = readVarint(data, i);
                int lenLen = varintLength(data, i);
                if (lenLen <= 0) {
                    return;
                }
                i += lenLen;
                int len = (int) lenLong;
                if (len < 0 || i + len > end) {
                    return;
                }
                if (field == 1 && name.isEmpty()) {
                    name = new String(data, i, len, StandardCharsets.UTF_8);
                }
                i += len;
            } else if (wire == 1) {
                i += 8;
            } else if (wire == 5) {
                i += 4;
            } else {
                return;
            }
        }
        if (!name.isEmpty()) {
            gift.user.fansMedal = new User.FansMedal();
            gift.user.fansMedal.name = name;
            gift.user.fansMedal.level = level;
        }
    }

    private static int varintLength(byte[] data, int offset) {
        int end = data.length;
        for (int i = offset; i < end && i < offset + 10; i++) {
            if ((data[i] & 0x80) == 0) {
                return i - offset + 1;
            }
        }
        return -1;
    }

    private static long readVarint(byte[] data, int offset) {
        long result = 0L;
        int shift = 0;
        for (int i = offset; i < data.length && shift < 64; i++) {
            int b = data[i] & 0xff;
            result |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        throw new IllegalArgumentException("invalid varint");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstString(JsonObject obj, String key, String fallback) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback == null ? "" : fallback;
        }
        return element.getAsString();
    }

    private static int firstInt(JsonObject obj, String key, int fallback) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static JsonElement firstObject(JsonObject obj, String... keys) {
        for (String key : keys) {
            JsonElement element = obj.get(key);
            if (element != null && element.isJsonObject()) {
                return element;
            }
        }
        return null;
    }

    private static JsonElement nested(JsonObject obj, String... path) {
        JsonElement current = obj;
        for (String key : path) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject().get(key);
        }
        return current;
    }

    private static String nestedString(JsonObject obj, String... path) {
        JsonElement element = nested(obj, path);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.getAsString();
    }

    private static int nestedInt(JsonObject obj, String... path) {
        JsonElement element = nested(obj, path);
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
