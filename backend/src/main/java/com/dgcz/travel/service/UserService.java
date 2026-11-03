package com.dgcz.travel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dgcz.travel.common.BusinessException;
import com.dgcz.travel.common.UserContext;
import com.dgcz.travel.dto.LoginRequest;
import com.dgcz.travel.dto.RegisterRequest;
import com.dgcz.travel.dto.UpdatePasswordRequest;
import com.dgcz.travel.dto.UpdateProfileRequest;
import com.dgcz.travel.entity.User;
import com.dgcz.travel.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 用户服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024L;
    private static final List<String> ALLOWED_EXT = List.of(".jpg", ".jpeg", ".png", ".webp", ".gif");

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    /** 头像保存目录（相对工作目录） */
    @Value("${app.upload.dir:uploads/avatar}")
    private String uploadDir;

    /** 头像访问前缀 */
    @Value("${app.upload.url-prefix:/uploads/avatar/}")
    private String urlPrefix;

    public User register(RegisterRequest req) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(StringUtils.hasText(req.getNickname()) ? req.getNickname() : req.getUsername());
        // 注册一律是普通用户，管理员只能由后台初始化/手工指定，避免越权注册
        user.setRole(UserContext.ROLE_USER);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    public User login(LoginRequest req) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        return user;
    }

    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    /* ------------------------------- 个人中心 ------------------------------- */

    /** 更新昵称 / 头像 */
    public User updateProfile(Long userId, UpdateProfileRequest req) {
        User user = requireUser(userId);
        if (req.getNickname() != null && StringUtils.hasText(req.getNickname())) {
            user.setNickname(req.getNickname().trim());
        }
        if (req.getAvatar() != null) {
            user.setAvatar(req.getAvatar().trim());
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return user;
    }

    /** 修改密码（校验原密码） */
    public void updatePassword(Long userId, UpdatePasswordRequest req) {
        if (req.getNewPassword().equals(req.getOldPassword())) {
            throw new BusinessException("新密码不能与旧密码相同");
        }
        User user = requireUser(userId);
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /** 上传头像，返回可访问地址 */
    public String uploadAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BusinessException("图片大小不能超过 2MB");
        }
        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持 jpg / jpeg / png / webp / gif 格式");
        }
        String filename = userId + "_" + System.currentTimeMillis() + ext;
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("头像保存失败", e);
            throw new BusinessException("头像保存失败：" + e.getMessage());
        }
        String prefix = urlPrefix.endsWith("/") ? urlPrefix : urlPrefix + "/";
        return prefix + filename;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }
}
