package com.dgcz.travel.common;

/**
 * 当前登录用户上下文（ThreadLocal）
 */
public class UserContext {

    /** 管理员角色标识 */
    public static final String ROLE_ADMIN = "ADMIN";

    /** 普通用户角色标识 */
    public static final String ROLE_USER = "USER";

    private static final ThreadLocal<Long> LOCAL = new ThreadLocal<>();

    /** 是否管理员：由 AdminInterceptor 逐个请求落库校验后写入，不信任前端传参 */
    private static final ThreadLocal<Boolean> ADMIN_LOCAL = new ThreadLocal<>();

    public static void set(Long userId) {
        LOCAL.set(userId);
    }

    public static Long get() {
        return LOCAL.get();
    }

    public static Long require() {
        Long id = LOCAL.get();
        if (id == null) {
            throw new BusinessException(401, "请先登录");
        }
        return id;
    }

    public static void setAdmin(boolean admin) {
        ADMIN_LOCAL.set(admin);
    }

    public static boolean isAdmin() {
        return Boolean.TRUE.equals(ADMIN_LOCAL.get());
    }

    public static void clear() {
        LOCAL.remove();
        ADMIN_LOCAL.remove();
    }
}
