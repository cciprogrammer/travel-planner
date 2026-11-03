package com.dgcz.travel.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台用户数据视图（不含密码等敏感字段）
 */
@Data
public class AdminUserVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    /** ADMIN / USER */
    private String role;

    /** 1-正常，0-已禁用 */
    private Integer status;

    private LocalDateTime createTime;

    /** 方案总数 */
    private Long planCount;

    /** 收藏总数 */
    private Long favoriteCount;

    /** 最近一次生成方案的时间 */
    private LocalDateTime lastPlanTime;
}
