package com.dgcz.travel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 酒店（管理员在后台维护，供行程住宿推荐参考）
 */
@Data
@TableName("t_hotel")
public class Hotel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 酒店名称 */
    private String name;

    /** 所在城市 */
    private String city;

    /** 所在区域 / 商圈 */
    private String area;

    /** 详细地址 */
    private String address;

    /** 档次：经济型 / 舒适型 / 高档型 */
    private String level;

    /** 每晚参考价（元） */
    private Integer pricePerNight;

    /** 评分 0.0-5.0 */
    private BigDecimal rating;

    /** 联系电话 */
    private String phone;

    /** 标签，逗号分隔 */
    private String tags;

    /** 简介 / 推荐理由 */
    private String description;

    /** 图片地址 */
    private String imageUrl;

    /** 状态：1-上架，0-下架 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
