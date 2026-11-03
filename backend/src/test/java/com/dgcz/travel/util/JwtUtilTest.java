package com.dgcz.travel.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 的签发 / 解析往返。
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "travel-planner-jwt-secret-key-please-change-2026");
        ReflectionTestUtils.setField(jwtUtil, "expireHours", 72L);
    }

    @Test
    @DisplayName("generate → parse：subject 是 userId，username 在 claim 里")
    void roundTrip() {
        String token = jwtUtil.generate(25L, "admin");
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "JWT 应有三段");

        Claims c = jwtUtil.parse(token);
        assertEquals("25", c.getSubject());
        assertEquals("admin", c.get("username"));
    }

    @Test
    @DisplayName("被篡改的 token 解析失败（签名校验生效）")
    void tamperedTokenThrows() {
        String token = jwtUtil.generate(1L, "u");
        String tampered = token.substring(0, token.length() - 3) + "abc";
        assertThrows(Exception.class, () -> jwtUtil.parse(tampered));
    }

    @Test
    @DisplayName("换一个密钥后旧 token 解析失败")
    void differentSecretRejects() {
        String token = jwtUtil.generate(1L, "u");
        JwtUtil other = new JwtUtil();
        ReflectionTestUtils.setField(other, "secret", "another-secret-key-with-enough-length-2026");
        ReflectionTestUtils.setField(other, "expireHours", 72L);
        assertThrows(Exception.class, () -> other.parse(token));
    }
}
