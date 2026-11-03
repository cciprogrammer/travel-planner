package com.dgcz.travel.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 行程生成请求
 */
@Data
public class PlanGenerateRequest {

    @NotBlank(message = "出发地不能为空")
    private String origin;

    @NotBlank(message = "目的地不能为空")
    private String destination;

    @Min(value = 1, message = "出行天数至少为 1 天")
    private Integer days = 3;

    /** 总预算（元） */
    private BigDecimal budget;

    /** 兴趣爱好，逗号分隔 */
    private String hobbies;

    /** 其他偏好：交通、住宿、节奏等 */
    private String preferences;

    /** 期望出发日期（可选，如 2026-10-01） */
    private String startDate;

    /** 同行人数说明（可选，如 2人/亲子/情侣） */
    private String companions;
}
