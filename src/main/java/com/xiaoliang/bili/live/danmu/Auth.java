package com.xiaoliang.bili.live.danmu;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Auth {
    private static final Gson GSON = new Gson();
    
    public final long uid;
    public final long roomid;
    @SuppressWarnings("unused")
    public final int protover = 2;
    public final String buvid;
    @SuppressWarnings("unused")
    public final String platform = "web";
    @SuppressWarnings("unused")
    public final int type = 2;
    public final String key;

    public Auth(long roomid, long uid, String buvid, String key) {
        this.roomid = roomid;
        this.uid = uid;
        this.buvid = buvid;
        this.key = key;
    }

    public static @NotNull Auth create(long roomid, String cookie) 
            throws IOException, InterruptedException {
        DanmuInfo info = getDanmuInfo(roomid, cookie);
        String buvid = extractCookieValue("buvid3", cookie);
        long uid = Long.parseLong(Objects.requireNonNull(
            extractCookieValue("DedeUserID", cookie)));
        return new Auth(info.roomId, uid, buvid, info.token);
    }

    public static @NotNull Auth create(long roomid) 
            throws IOException, InterruptedException {
        DanmuInfo info = getDanmuInfo(roomid, "");
        String buvid = generateUUID();
        return new Auth(info.roomId, 0, buvid, info.token);
    }

    private static DanmuInfo getDanmuInfo(long roomId, String cookie) 
            throws IOException, InterruptedException {
        Map<String, Object> params = new HashMap<>();
        params.put("id", roomId);
        params.put("type", "0");

        String signedQuery;
        try {
            signedQuery = BiliWbiSign.wbiSign(params);
        } catch (Exception e) {
            throw new RuntimeException("签名失败", e);
        }
        
        String baseUrl = "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo";
        String fullUrl = baseUrl + "?" + signedQuery;

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(fullUrl))
                .header("Accept", "*/*")
                .header("Cookie", cookie);

        HttpResponse<String> response = BiliHttp.send(requestBuilder);
        if (response.statusCode() != 200) {
            throw new IOException("getDanmuInfo HTTP " + response.statusCode());
        }

        JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
        int code = root.has("code") ? root.get("code").getAsInt() : -1;
        if (code != 0) {
            throw new IOException("getDanmuInfo 接口返回错误 code=" + code
                    + " message=" + (root.has("message") ? root.get("message").getAsString() : "")
                    + " body=" + response.body());
        }
        JsonObject data = root.has("data") && !root.get("data").isJsonNull()
                ? root.getAsJsonObject("data") : null;
        if (data == null || !data.has("token") || data.get("token").isJsonNull()) {
            throw new IOException("getDanmuInfo 响应缺少 token 字段: " + response.body());
        }
        // 认证使用真实房间号（room_id）。新版接口已移除 room_id 字段，
        // 存在时优先使用（兼容短房间号），缺失时回退到入参房间号。
        long realRoomId = data.has("room_id") && !data.get("room_id").isJsonNull()
                ? data.get("room_id").getAsLong() : roomId;
        return new DanmuInfo(realRoomId, data.get("token").getAsString());
    }

    /** getDanmuInfo 接口返回的认证信息 */
    private record DanmuInfo(long roomId, String token) {
    }

    private static @Nullable String extractCookieValue(String cookieName, 
                                                       @NotNull String cookieString) {
        String[] cookies = cookieString.split(";");
        for (String cookie : cookies) {
            cookie = cookie.trim();
            if (cookie.startsWith(cookieName + "=")) {
                return cookie.substring(cookieName.length() + 1);
            }
        }
        return null;
    }

    private static @NotNull String generateUUID() {
        UUID uuid = UUID.randomUUID();
        long currentTimeMillis = System.currentTimeMillis();
        String timestampSuffix = String.valueOf(currentTimeMillis).substring(0, 5);
        return uuid.toString().replace("-", "") + timestampSuffix + "infoc";
    }
}
