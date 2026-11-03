package com.dgcz.travel.controller;

import com.dgcz.travel.common.Result;
import com.dgcz.travel.common.UserContext;
import com.dgcz.travel.dto.UpdatePasswordRequest;
import com.dgcz.travel.dto.UpdateProfileRequest;
import com.dgcz.travel.entity.User;
import com.dgcz.travel.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 个人中心：资料 / 头像 / 密码
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 当前登录用户信息 */
    @GetMapping("/profile")
    public Result<User> profile() {
        return Result.ok(userService.getById(UserContext.require()));
    }

    /** 修改昵称 / 头像 */
    @PutMapping("/profile")
    public Result<User> updateProfile(@RequestBody @Valid UpdateProfileRequest req) {
        return Result.ok(userService.updateProfile(UserContext.require(), req));
    }

    /** 修改密码 */
    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestBody @Valid UpdatePasswordRequest req) {
        userService.updatePassword(UserContext.require(), req);
        return Result.ok();
    }

    /** 上传头像（multipart/form-data，字段名为 file） */
    @PostMapping("/avatar")
    public Result<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String url = userService.uploadAvatar(UserContext.require(), file);
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        return Result.ok(data);
    }
}
