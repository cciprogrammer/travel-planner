package com.dgcz.travel.interceptor;

import com.dgcz.travel.common.UserContext;
import com.dgcz.travel.entity.User;
import com.dgcz.travel.mapper.UserMapper;
import com.dgcz.travel.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 白盒测试 P2：JwtInterceptor.preHandle 的分支覆盖。
 *
 * 重点覆盖「回库校验账号状态」这条新增分支——它决定了禁用用户能否立刻被踢下线。
 */
class JwtInterceptorTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final JwtInterceptor interceptor = new JwtInterceptor(jwtUtil, userMapper);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private User user(long id, String role, int status) {
        User u = new User();
        u.setId(id);
        u.setUsername("tester");
        u.setRole(role);
        u.setStatus(status);
        return u;
    }

    /**
     * 生成一个真实的 Claims 对象（不 mock）。
     * 注意：不能在 when(...).thenReturn(...) 的实参里再调 Mockito 的 when，
     * 那样会触发 UnfinishedStubbing；用真实 JWT 解析最省事。
     */
    private Claims claimsOf(String subject) {
        JwtUtil real = new JwtUtil();
        org.springframework.test.util.ReflectionTestUtils.setField(
                real, "secret", "travel-planner-jwt-secret-key-please-change-2026");
        org.springframework.test.util.ReflectionTestUtils.setField(real, "expireHours", 72L);
        return real.parse(real.generate(Long.valueOf(subject), "tester"));
    }

    @Test
    @DisplayName("没有 Authorization 头 → 401，不放行")
    void noHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/plans");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req, resp, new Object()));
        assertEquals(401, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("未登录"));
    }

    @Test
    @DisplayName("token 解析失败 → 401「登录已过期」")
    void badToken() throws Exception {
        when(jwtUtil.parse(anyString())).thenThrow(new RuntimeException("bad signature"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/plans");
        req.addHeader("Authorization", "Bearer not.a.real.token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req, resp, new Object()));
        assertEquals(401, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("登录已过期"));
    }

    @Test
    @DisplayName("合法 token + 账号正常 → 放行并写入 userId 与角色")
    void validToken() throws Exception {
        when(jwtUtil.parse("tk")).thenReturn(claimsOf("25"));
        when(userMapper.selectById(25L)).thenReturn(user(25L, "ADMIN", 1));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/plans");
        req.addHeader("Authorization", "Bearer tk");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(req, resp, new Object()));
        assertEquals(25L, UserContext.get());
        assertTrue(UserContext.isAdmin(), "ADMIN 角色应被识别为管理员");

        interceptor.afterCompletion(req, resp, new Object(), null);
        assertNull(UserContext.get(), "afterCompletion 必须清理 ThreadLocal");
    }

    @Test
    @DisplayName("账号已被删除（回库为空）→ 401，旧 token 失效")
    void userGone() throws Exception {
        when(jwtUtil.parse("tk")).thenReturn(claimsOf("25"));
        when(userMapper.selectById(25L)).thenReturn(null);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/plans");
        req.addHeader("Authorization", "Bearer tk");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req, resp, new Object()));
        assertEquals(401, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("账号不存在"));
    }

    @Test
    @DisplayName("账号已被禁用 → 401，已签发的 token 立即失效")
    void userDisabled() throws Exception {
        when(jwtUtil.parse("tk")).thenReturn(claimsOf("26"));
        when(userMapper.selectById(26L)).thenReturn(user(26L, "USER", 0));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/plans");
        req.addHeader("Authorization", "Bearer tk");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req, resp, new Object()));
        assertEquals(401, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("账号已被禁用"));
        assertNull(UserContext.get());
    }

    @Test
    @DisplayName("普通用户 → 不是管理员")
    void normalUserIsNotAdmin() throws Exception {
        when(jwtUtil.parse("tk")).thenReturn(claimsOf("26"));
        when(userMapper.selectById(26L)).thenReturn(user(26L, "USER", 1));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/plans");
        req.addHeader("Authorization", "Bearer tk");
        assertTrue(interceptor.preHandle(req, new MockHttpServletResponse(), new Object()));
        assertFalse(UserContext.isAdmin());
    }

    /**
     * 已知缺陷 D5 的现状锁定：不带 "Bearer " 前缀的 token 仍会被接受。
     * 修复后此用例应改为 assertFalse。
     */
    @Test
    @DisplayName("已知缺陷 D5：缺 Bearer 前缀的 token 仍被接受")
    void tokenWithoutBearerPrefix_knownDefectD5() throws Exception {
        when(jwtUtil.parse("tk")).thenReturn(claimsOf("25"));
        when(userMapper.selectById(25L)).thenReturn(user(25L, "USER", 1));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/plans");
        req.addHeader("Authorization", "tk");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(req, resp, new Object()), "当前实现会放行（缺陷 D5）");
        assertEquals(200, resp.getStatus());
    }

    @Test
    @DisplayName("OPTIONS 预检直接放行")
    void optionsPasses() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("OPTIONS", "/api/plans");
        assertTrue(interceptor.preHandle(req, new MockHttpServletResponse(), new Object()));
    }
}
