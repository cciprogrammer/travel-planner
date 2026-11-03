package com.dgcz.travel.interceptor;

import com.dgcz.travel.common.UserContext;
import com.dgcz.travel.entity.User;
import com.dgcz.travel.mapper.UserMapper;
import com.dgcz.travel.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * JWT 登录拦截器
 *
 * <p>除了校验签名与有效期，还会回库确认账号仍然存在且未被禁用，
 * 这样管理员在后台禁用某个用户后，该用户已签发的 Token 会立即失效，
 * 不必等到下次登录。同时把角色写入 UserContext，供 AdminInterceptor 判断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            write(response, 401, "未登录，请先登录");
            return false;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Long userId;
        try {
            Claims claims = jwtUtil.parse(token);
            userId = Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            log.warn("Token 解析失败：{}", e.getMessage());
            write(response, 401, "登录已过期，请重新登录");
            return false;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            write(response, 401, "账号不存在，请重新登录");
            return false;
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            write(response, 401, "账号已被禁用，请联系管理员");
            return false;
        }

        UserContext.set(userId);
        UserContext.setAdmin(UserContext.ROLE_ADMIN.equalsIgnoreCase(user.getRole()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private void write(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + msg + "\",\"data\":null}");
    }
}
