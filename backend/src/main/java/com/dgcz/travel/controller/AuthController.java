package com.dgcz.travel.controller;

import com.dgcz.travel.common.Result;
import com.dgcz.travel.common.UserContext;
import com.dgcz.travel.dto.LoginRequest;
import com.dgcz.travel.dto.LoginResponse;
import com.dgcz.travel.dto.RegisterRequest;
import com.dgcz.travel.entity.User;
import com.dgcz.travel.service.UserService;
import com.dgcz.travel.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody @Valid RegisterRequest req) {
        User user = userService.register(req);
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return Result.ok(new LoginResponse(token, user));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest req) {
        User user = userService.login(req);
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return Result.ok(new LoginResponse(token, user));
    }

    @GetMapping("/me")
    public Result<User> me() {
        return Result.ok(userService.getById(UserContext.require()));
    }
}
