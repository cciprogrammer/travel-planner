package com.dgcz.travel.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 酒店新增 / 编辑请求
 */
@Data
public class HotelRequest {

    @NotBlank(message = "酒店名称不能为空")
    @Size(max = 100, message = "酒店名称不能超过 100 字")
    private String name;

    @NotBlank(message = "所在城市不能为空")
    @Size(max = 50, message = "城市名称不能超过 50 字")
    private String city;

    @Size(max = 100, message = "所在区域不能超过 100 字")
    private String area;

    @Size(max = 255, message = "详细地址不能超过 255 字")
    private String address;

    @Size(max = 20, message = "档次不能超过 20 字")
    private String level;

    @NotNull(message = "每晚参考价不能为空")
    @Min(value = 0, message = "每晚参考价不能小于 0")
    @Max(value = 100000, message = "每晚参考价不能超过 100000 元")
    private Integer pricePerNight;

    @Min(value = 0, message = "评分不能小于 0")
    @Max(value = 5, message = "评分不能超过 5")
    private BigDecimal rating;

    @Size(max = 50, message = "联系电话不能超过 50 字")
    private String phone;

    @Size(max = 255, message = "标签不能超过 255 字")
    private String tags;

    @Size(max = 500, message = "简介不能超过 500 字")
    private String description;

    @Size(max = 255, message = "图片地址不能超过 255 字")
    private String imageUrl;

    /** 1-上架，0-下架；新增时不传默认上架 */
    private Integer status;
}
