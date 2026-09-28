package com.wzbc.studyroom.security;

/** 当前请求登录用户（ThreadLocal） */
public class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getId();
    }

    public static Integer role() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getRole();
    }

    /**
     * 当前用户的数据访问范围（门店 id）。
     * <p>Service 层拼查询条件时应统一用它，禁止直接从请求参数里取 storeId
     * ——否则前台只要改一下传参就能查别的门店。
     */
    public static Long storeId() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getStoreId();
    }

    /** 是否经营者（0）。经营者不受门店范围限制。 */
    public static boolean isOwner() {
        Integer r = role();
        return r != null && r == 0;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
