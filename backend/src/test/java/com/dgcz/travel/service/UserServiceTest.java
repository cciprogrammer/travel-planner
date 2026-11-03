package com.dgcz.travel.service;

import com.dgcz.travel.common.BusinessException;
import com.dgcz.travel.dto.LoginRequest;
import com.dgcz.travel.dto.RegisterRequest;
import com.dgcz.travel.entity.User;
import com.dgcz.travel.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 白盒测试：注册固定为普通用户、登录时的状态与密码分支。
 */
class UserServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserService service = new UserService(userMapper, encoder);

    private User persisted(int status, String rawPassword) {
        User u = new User();
        u.setId(26L);
        u.setUsername("tester");
        u.setPassword(encoder.encode(rawPassword));
        u.setRole("USER");
        u.setStatus(status);
        return u;
    }

    private LoginRequest login(String username, String password) {
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword(password);
        return r;
    }

    @Test
    @DisplayName("注册：固定 role=USER、status=1，密码以 BCrypt 存储")
    void registerForcesNormalRole() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> 1);

        RegisterRequest req = new RegisterRequest();
        req.setUsername("newbie");
        req.setPassword("123456");

        User u = service.register(req);

        assertEquals("USER", u.getRole(), "注册不能带出管理员角色");
        assertEquals(1, u.getStatus());
        assertNotEquals("123456", u.getPassword(), "明文密码不能落库");
        assertTrue(encoder.matches("123456", u.getPassword()));
    }

    @Test
    @DisplayName("注册：用户名重复 → 400")
    void registerDuplicate() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        RegisterRequest req = new RegisterRequest();
        req.setUsername("admin");
        req.setPassword("123456");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.register(req));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("用户名已存在"));
    }

    @Test
    @DisplayName("登录：账号被禁用 → 403，提示联系管理员")
    void loginDisabled() {
        when(userMapper.selectOne(any())).thenReturn(persisted(0, "123456"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(login("tester", "123456")));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("已被禁用"));
    }

    @Test
    @DisplayName("登录：密码错误 → 400")
    void loginWrongPassword() {
        when(userMapper.selectOne(any())).thenReturn(persisted(1, "123456"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(login("tester", "wrongpwd")));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("用户名或密码错误"));
    }

    @Test
    @DisplayName("登录：用户不存在 → 400")
    void loginNoSuchUser() {
        when(userMapper.selectOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(login("nouser", "123456")));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("用户不存在"));
    }

    @Test
    @DisplayName("登录：账号正常 → 返回用户")
    void loginOk() {
        when(userMapper.selectOne(any())).thenReturn(persisted(1, "123456"));
        User u = service.login(login("tester", "123456"));
        assertNotNull(u);
        assertEquals(26L, u.getId());
    }
}
