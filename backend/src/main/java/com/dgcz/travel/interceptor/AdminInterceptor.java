package com.dgcz.travel.interceptor;

import com.dgcz.travel.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 管理员权限拦截器
 *
 * <p>挂在 /api/admin/** 上，在 JwtInterceptor 之后执行。
 * JwtInterceptor 已经回库校验过账号存在、未被禁用，并把角色写进了 UserContext，
 * 所以这里只需判断角色，不重复查库。
 */
@Slf4j
@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (UserContext.get() == null) {
            write(response, 401, "未登录，请先登录");
            return false;
        }
        if (!UserContext.isAdmin()) {
            log.warn("非管理员访问后台接口：userId={} uri={}", UserContext.get(), request.getRequestURI());
            write(response, 403, "需要管理员权限");
            return false;
        }
        return true;
    }

    private void write(HttpServletResponse response, int status, String msg) throws IOException {
        // 用真实 HTTP 状态码，前端 axios 的 error 分支会直接展示 message
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + msg + "\",\"data\":null}");
    }
}
