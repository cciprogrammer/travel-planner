package com.dgcz.travel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 地点收藏请求
 */
@Data
public class FavoriteRequest {

    @NotBlank(message = "地点名称不能为空")
    private String name;

    private Long planId;
    private String city;
    private String category;
    private String address;
    private String description;
    private String rating;
    private String imageUrl;
}
