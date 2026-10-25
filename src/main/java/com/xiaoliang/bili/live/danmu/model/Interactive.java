package com.xiaoliang.bili.live.danmu.model;

import com.xiaoliang.bili.live.danmu.User;

public class Interactive {
    public static final int ENTER = 1;
    public static final int FOLLOW = 2;
    public static final int SHARE = 3;
    public static final int SPECIAL_FOLLOW = 4;
    public static final int MUTUAL_FOLLOW = 5;
    public static final int LIKE = 6;

    public User user = new User();
    public int type;

    /**
     * 点赞文案。进场、关注等互动为空。
     */
    public String text = "";

    public static String typeName(int type) {
        return switch (type) {
            case ENTER -> "ENTER";
            case FOLLOW -> "FOLLOW";
            case SHARE -> "SHARE";
            case SPECIAL_FOLLOW -> "SPECIAL_FOLLOW";
            case MUTUAL_FOLLOW -> "MUTUAL_FOLLOW";
            case LIKE -> "LIKE";
            default -> "UNKNOWN";
        };
    }
}
