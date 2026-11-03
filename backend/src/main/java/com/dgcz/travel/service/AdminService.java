package com.dgcz.travel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dgcz.travel.common.BusinessException;
import com.dgcz.travel.common.UserContext;
import com.dgcz.travel.dto.AdminStatsVO;
import com.dgcz.travel.dto.AdminUserVO;
import com.dgcz.travel.dto.PlanVO;
import com.dgcz.travel.entity.FavoritePlace;
import com.dgcz.travel.entity.Hotel;
import com.dgcz.travel.entity.TravelPlan;
import com.dgcz.travel.entity.User;
import com.dgcz.travel.mapper.FavoritePlaceMapper;
import com.dgcz.travel.mapper.HotelMapper;
import com.dgcz.travel.mapper.TravelPlanMapper;
import com.dgcz.travel.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台数据服务：数据概览 + 用户数据检测
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final int MAX_PAGE_SIZE = 100;
    /** 趋势统计天数（含今天） */
    private static final int TREND_DAYS = 7;

    private final UserMapper userMapper;
    private final TravelPlanMapper planMapper;
    private final FavoritePlaceMapper favoriteMapper;
    private final HotelMapper hotelMapper;
    private final PlanService planService;

    /* ------------------------------- 数据概览 ------------------------------- */

    public AdminStatsVO overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().minusDays(TREND_DAYS - 1L).atStartOfDay();

        AdminStatsVO vo = new AdminStatsVO();
        vo.setTotalUsers(userMapper.selectCount(new LambdaQueryWrapper<>()));
        vo.setActiveUsers(userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1)));
        vo.setTotalPlans(planMapper.selectCount(new LambdaQueryWrapper<>()));
        vo.setTotalFavorites(favoriteMapper.selectCount(new LambdaQueryWrapper<>()));
        vo.setTotalHotels(hotelMapper.selectCount(new LambdaQueryWrapper<>()));

        vo.setNewUsersToday(userMapper.selectCount(
                new LambdaQueryWrapper<User>().ge(User::getCreateTime, todayStart)));
        vo.setNewUsers7d(userMapper.selectCount(
                new LambdaQueryWrapper<User>().ge(User::getCreateTime, weekStart)));
        vo.setPlansToday(planMapper.selectCount(
                new LambdaQueryWrapper<TravelPlan>().ge(TravelPlan::getCreateTime, todayStart)));
        vo.setPlans7d(planMapper.selectCount(
                new LambdaQueryWrapper<TravelPlan>().ge(TravelPlan::getCreateTime, weekStart)));

        vo.setPlanTrend(planTrend(weekStart));
        vo.setHotelByCity(hotelByCity());
        vo.setTopUsers(topUsers());
        return vo;
    }

    /** 近 7 天每日生成的方案数（无数据的日期补 0，保证折线连续） */
    private List<Map<String, Object>> planTrend(LocalDateTime weekStart) {
        // 用 DATE_FORMAT 直接拿字符串日期，避免不同驱动把 DATE 映射成 Date / LocalDate 的差异
        QueryWrapper<TravelPlan> wrapper = new QueryWrapper<TravelPlan>()
                .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS d", "COUNT(*) AS cnt")
                .ge("create_time", weekStart)
                .groupBy("DATE_FORMAT(create_time, '%Y-%m-%d')");

        Map<String, Long> counted = new HashMap<>();
        for (Map<String, Object> row : planMapper.selectMaps(wrapper)) {
            counted.put(String.valueOf(pick(row, "d", "D")), toLong(pick(row, "cnt", "CNT")));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            String date = LocalDate.now().minusDays(i).toString();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("count", counted.getOrDefault(date, 0L));
            result.add(item);
        }
        return result;
    }

    /** 酒店按城市分布（Top 10） */
    private List<Map<String, Object>> hotelByCity() {
        QueryWrapper<Hotel> wrapper = new QueryWrapper<Hotel>()
                .select("city AS city", "COUNT(*) AS cnt")
                .groupBy("city")
                .orderByDesc("cnt")
                .last("LIMIT 10");

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : hotelMapper.selectMaps(wrapper)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("city", String.valueOf(pick(row, "city", "CITY")));
            item.put("count", toLong(pick(row, "cnt", "CNT")));
            result.add(item);
        }
        return result;
    }

    /** 方案数最多的前 5 位用户 */
    private List<Map<String, Object>> topUsers() {
        QueryWrapper<TravelPlan> wrapper = new QueryWrapper<TravelPlan>()
                .select("user_id AS userId", "COUNT(*) AS cnt")
                .groupBy("user_id")
                .orderByDesc("cnt")
                .last("LIMIT 5");

        List<Map<String, Object>> rows = planMapper.selectMaps(wrapper);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            ids.add(toLong(pick(row, "userId", "user_id", "USERID")));
        }
        Map<Long, User> userMap = new HashMap<>();
        for (User u : userMapper.selectBatchIds(ids)) {
            userMap.put(u.getId(), u);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long uid = toLong(pick(row, "userId", "user_id", "USERID"));
            User u = userMap.get(uid);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", uid);
            item.put("username", u == null ? "(已删除)" : u.getUsername());
            item.put("nickname", u == null ? "" : u.getNickname());
            item.put("planCount", toLong(pick(row, "cnt", "CNT")));
            result.add(item);
        }
        return result;
    }

    /* ------------------------------- 用户数据 ------------------------------- */

    /**
     * 用户分页（含每人方案数 / 收藏数 / 最近生成时间）。
     * 方案数与收藏数用两条分组查询批量取回，避免逐条查询造成的 N+1。
     */
    public IPage<AdminUserVO> pageUsers(int pageNum, int pageSize, String keyword,
                                        String role, Integer status) {
        int num = Math.max(1, pageNum);
        int size = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, kw).or().like(User::getNickname, kw));
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq(User::getRole, role.trim().toUpperCase());
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreateTime).orderByDesc(User::getId);

        IPage<User> page = userMapper.selectPage(new Page<>(num, size), wrapper);
        List<User> users = page.getRecords();
        if (users.isEmpty()) {
            return page.convert(this::toVO);
        }

        List<Long> ids = users.stream().map(User::getId).toList();

        Map<Long, Long> planCounts = new HashMap<>();
        Map<Long, LocalDateTime> lastPlanTimes = new HashMap<>();
        for (Map<String, Object> row : planMapper.selectMaps(new QueryWrapper<TravelPlan>()
                .select("user_id AS userId", "COUNT(*) AS cnt", "MAX(create_time) AS lastTime")
                .in("user_id", ids)
                .groupBy("user_id"))) {
            Long uid = toLong(pick(row, "userId", "user_id", "USERID"));
            planCounts.put(uid, toLong(pick(row, "cnt", "CNT")));
            lastPlanTimes.put(uid, toDateTime(pick(row, "lastTime", "lasttime", "LASTTIME")));
        }

        Map<Long, Long> favoriteCounts = new HashMap<>();
        for (Map<String, Object> row : favoriteMapper.selectMaps(new QueryWrapper<FavoritePlace>()
                .select("user_id AS userId", "COUNT(*) AS cnt")
                .in("user_id", ids)
                .groupBy("user_id"))) {
            Long uid = toLong(pick(row, "userId", "user_id", "USERID"));
            favoriteCounts.put(uid, toLong(pick(row, "cnt", "CNT")));
        }

        return page.convert(u -> {
            AdminUserVO vo = toVO(u);
            vo.setPlanCount(planCounts.getOrDefault(u.getId(), 0L));
            vo.setFavoriteCount(favoriteCounts.getOrDefault(u.getId(), 0L));
            vo.setLastPlanTime(lastPlanTimes.get(u.getId()));
            return vo;
        });
    }

    /** 禁用 / 启用某个用户 */
    public void updateStatus(Long operatorId, Long userId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态只能是 1（正常）或 0（禁用）");
        }
        if (userId.equals(operatorId)) {
            throw new BusinessException("不能修改自己的账号状态");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (UserContext.ROLE_ADMIN.equalsIgnoreCase(user.getRole())) {
            throw new BusinessException("不能禁用其他管理员账号");
        }
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        log.info("管理员 {} 将用户 {} 状态改为 {}", operatorId, userId, status);
    }

    /** 查看某个用户生成的方案（只读） */
    public IPage<PlanVO> userPlans(Long userId, int pageNum, int pageSize, String keyword) {
        if (userMapper.selectById(userId) == null) {
            throw new BusinessException(404, "用户不存在");
        }
        int size = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        return planService.page(userId, Math.max(1, pageNum), size,
                StringUtils.hasText(keyword) ? keyword.trim() : null);
    }

    /* ------------------------------- 工具方法 ------------------------------- */

    private AdminUserVO toVO(User u) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setAvatar(u.getAvatar());
        vo.setRole(u.getRole());
        vo.setStatus(u.getStatus());
        vo.setCreateTime(u.getCreateTime());
        vo.setPlanCount(0L);
        vo.setFavoriteCount(0L);
        return vo;
    }

    /** Map 结果取值：不同驱动/配置下字段名大小写不一致，逐个候选键尝试 */
    private Object pick(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            if (row.containsKey(k)) {
                return row.get(k);
            }
        }
        return null;
    }

    private long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private LocalDateTime toDateTime(Object v) {
        if (v instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (v instanceof java.time.Instant instant) {
            return LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate().atStartOfDay();
        }
        return null;
    }
}
