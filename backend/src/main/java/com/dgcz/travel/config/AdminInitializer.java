package com.dgcz.travel.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dgcz.travel.common.UserContext;
import com.dgcz.travel.entity.User;
import com.dgcz.travel.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 管理员账号初始化
 *
 * <p>启动时确保存在一个管理员账号（账号名由 app.admin.username 指定，密码必须通过环境变量
 * ADMIN_PASSWORD 注入；未配置则跳过创建，避免仓库里出现默认口令）。
 * 用代码初始化而不是在 SQL 里写死 BCrypt 密文，可以保证密文一定和项目里的加密器匹配。
 * 已存在时只补齐角色与状态，不会覆盖密码——避免每次重启把管理员改过的密码重置回去。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.nickname:系统管理员}")
    private String adminNickname;

    @Override
    public void run(ApplicationArguments args) {
        try {
            init();
        } catch (Exception e) {
            // 初始化失败不应阻断整个应用启动，但要明确告警
            log.error("管理员账号初始化失败：{}", e.getMessage());
        }
    }

    private void init() {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("未配置 ADMIN_PASSWORD，跳过管理员账号创建（如需创建请注入该环境变量）");
            return;
        }
        User exist = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, adminUsername));

        if (exist == null) {
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setNickname(adminNickname);
            admin.setRole(UserContext.ROLE_ADMIN);
            admin.setStatus(1);
            admin.setCreateTime(LocalDateTime.now());
            admin.setUpdateTime(LocalDateTime.now());
            userMapper.insert(admin);
            log.info("已创建管理员账号：{}（初始密码 {}，请登录后尽快修改）", adminUsername, adminPassword);
            return;
        }

        boolean needFix = !UserContext.ROLE_ADMIN.equalsIgnoreCase(exist.getRole())
                || exist.getStatus() == null || exist.getStatus() != 1;
        if (needFix) {
            User patch = new User();
            patch.setId(exist.getId());
            patch.setRole(UserContext.ROLE_ADMIN);
            patch.setStatus(1);
            patch.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(patch);
            log.info("已修正管理员账号 {} 的角色/状态为 ADMIN / 1", adminUsername);
        } else {
            log.info("管理员账号 {} 已存在，跳过初始化", adminUsername);
        }
    }
}
