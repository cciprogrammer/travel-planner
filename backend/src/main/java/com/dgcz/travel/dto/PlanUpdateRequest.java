package com.dgcz.travel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 行程编辑保存请求
 */
@Data
public class PlanUpdateRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String summary;

    /** 前端回传的完整行程结构 */
    private Itinerary itinerary;
}
