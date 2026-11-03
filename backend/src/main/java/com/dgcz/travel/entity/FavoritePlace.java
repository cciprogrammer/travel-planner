package com.dgcz.travel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 地点收藏
 */
@Data
@TableName("t_favorite_place")
public class FavoritePlace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 来源方案，可为空 */
    private Long planId;

    private String name;

    private String city;

    private String category;

    private String address;

    private String description;

    private String rating;

    private String imageUrl;

    private LocalDateTime createTime;
}
