package com.dgcz.travel.config;

import com.dgcz.travel.interceptor.AdminInterceptor;
import com.dgcz.travel.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web 配置：跨域 + JWT 拦截 + 头像静态资源映射
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final AdminInterceptor adminInterceptor;

    /** 头像保存目录，需与 UserService 保持一致 */
    @Value("${app.upload.dir:uploads/avatar}")
    private String uploadDir;

    /** 头像访问前缀 */
    @Value("${app.upload.url-prefix:/uploads/avatar/}")
    private String urlPrefix;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /** 头像等上传文件的静态访问映射 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        String handler = urlPrefix.endsWith("/") ? urlPrefix + "**" : urlPrefix + "/**";
        registry.addResourceHandler(handler).addResourceLocations(location);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 先登录校验，再管理员校验（顺序即执行顺序）
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/error"
                );
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**");
    }
}
