package com.dgcz.travel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 旅行方案
 */
@Data
@TableName("t_travel_plan")
public class TravelPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String origin;

    private String destination;

    private Integer days;

    private BigDecimal budget;

    /** 兴趣爱好，逗号分隔 */
    private String hobbies;

    /** 其他偏好：交通方式、住宿档次、出行节奏等 */
    private String preferences;

    /** 方案总览 */
    private String summary;

    /** 大模型返回的结构化行程（JSON 字符串） */
    private String itineraryJson;

    /** 生成模型 */
    private String model;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
