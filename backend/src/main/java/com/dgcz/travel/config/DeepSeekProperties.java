package com.dgcz.travel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 大模型配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    /** API Key */
    private String apiKey;

    /** 接口地址 */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** 超时时间（秒） */
    private Integer timeoutSeconds = 120;

    /** 单次生成最大输出 token 数（行程 JSON 较长，给足余量避免被截断） */
    private Integer maxTokens = 8192;

    /** 未配置 Key 时是否返回模拟数据 */
    private Boolean mockEnabled = false;

    /** 大模型调用失败（如 Key 无效、超时）时是否降级为内置示例数据，本地演示建议开启 */
    private Boolean fallbackToMockOnError = true;
}
