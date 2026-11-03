package com.dgcz.travel;

import com.dgcz.travel.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 基于大模型的旅行规划助手 —— 后端启动类
 */
@Slf4j
@SpringBootApplication
@MapperScan("com.dgcz.travel.mapper")
@RequiredArgsConstructor
public class TravelPlannerApplication implements CommandLineRunner {

    private final UserMapper userMapper;

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(TravelPlannerApplication.class, args);
        // 打印真实监听端口：本地默认用 8081 覆盖，写死 8080 会误导排查
        String port = ctx.getEnvironment().getProperty("local.server.port",
                ctx.getEnvironment().getProperty("server.port", "8080"));
        log.info("旅行规划助手后端启动成功：http://localhost:{}", port);
    }

    @Override
    public void run(String... args) {
        log.info("当前用户数：{}", userMapper.selectCount(null));
    }
}
