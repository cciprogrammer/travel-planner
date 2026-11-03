package com.dgcz.travel.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 后台数据概览
 */
@Data
public class AdminStatsVO {

    /** 用户总数（不含已逻辑删除） */
    private Long totalUsers;

    /** 正常状态用户数 */
    private Long activeUsers;

    /** 方案总数 */
    private Long totalPlans;

    /** 收藏总数 */
    private Long totalFavorites;

    /** 酒店总数（含下架） */
    private Long totalHotels;

    /** 今日新增用户 */
    private Long newUsersToday;

    /** 近 7 天新增用户 */
    private Long newUsers7d;

    /** 今日生成方案数 */
    private Long plansToday;

    /** 近 7 天生成方案数 */
    private Long plans7d;

    /** 近 7 天每日方案数，元素形如 {date: '2026-10-26', count: 3} */
    private List<Map<String, Object>> planTrend;

    /** 酒店城市分布，元素形如 {city: '长沙', count: 3} */
    private List<Map<String, Object>> hotelByCity;

    /** 活跃用户 Top（按方案数），元素为 AdminUserVO 的简化形式 */
    private List<Map<String, Object>> topUsers;
}
