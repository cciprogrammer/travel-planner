package com.dgcz.travel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dgcz.travel.common.BusinessException;
import com.dgcz.travel.common.UserContext;
import com.dgcz.travel.dto.Itinerary;
import com.dgcz.travel.dto.PlanGenerateRequest;
import com.dgcz.travel.dto.PlanUpdateRequest;
import com.dgcz.travel.dto.PlanVO;
import com.dgcz.travel.entity.TravelPlan;
import com.dgcz.travel.mapper.TravelPlanMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 旅行方案服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private final TravelPlanMapper planMapper;
    private final ItineraryPipeline pipeline;
    private final ItineraryStandard standard;
    private final ObjectMapper objectMapper;

    /**
     * 输出一行结构化埋点，字段用 | 分隔、k=v 形式，便于 grep + 脚本聚合。
     * 示例：GEN_METRIC|planId=41|dest=成都|days=3|llmCalls=1|firstPassed=true|finalPassed=true|totalMs=16420
     */
    private void logMetric(GenMetric m) {
        log.info("GEN_METRIC|planId={}|dest={}|days={}|llm={}|degraded={}|llmCalls={}|llmMs={}"
                        + "|firstPassed={}|firstIssues={}|firstScore={}|repaired={}|afterRepairIssues={}"
                        + "|rewrote={}|afterRewriteIssues={}|fellBack={}|afterFallbackIssues={}"
                        + "|finalPassed={}|finalIssues={}|finalScore={}|stage={}|source={}|totalMs={}",
                m.planId, m.destination, m.days, m.fromLlm, m.degraded, m.llmCalls, m.llmMs,
                m.firstPassed, m.firstIssues, m.firstScore, m.repaired, m.afterRepairIssues,
                m.rewrote, m.afterRewriteIssues, m.fellBack, m.afterFallbackIssues,
                m.finalPassed, m.finalIssues, m.finalScore, m.stageReached, m.source, m.totalMs);
    }

    /**
     * 调用大模型生成行程并保存
     */
    public Long generate(Long userId, PlanGenerateRequest req) {
        if (req.getDays() == null || req.getDays() < 1) {
            req.setDays(1);
        }
        if (req.getDays() > 30) {
            throw new BusinessException("单次生成天数请控制在 30 天以内");
        }
        GenMetric metric = new GenMetric();
        Itinerary itinerary = pipeline.run(req, metric);

        TravelPlan plan = new TravelPlan();
        plan.setUserId(userId);
        plan.setTitle(itinerary.getTitle());
        plan.setOrigin(req.getOrigin());
        plan.setDestination(req.getDestination());
        plan.setDays(req.getDays());
        plan.setBudget(req.getBudget() == null ? BigDecimal.ZERO : req.getBudget());
        plan.setHobbies(req.getHobbies());
        plan.setPreferences(req.getPreferences());
        plan.setSummary(itinerary.getSummary());
        plan.setItineraryJson(toJson(itinerary));
        plan.setModel("DeepSeek");
        plan.setCreateTime(LocalDateTime.now());
        plan.setUpdateTime(LocalDateTime.now());
        planMapper.insert(plan);
        metric.planId = plan.getId();
        logMetric(metric);
        return plan.getId();
    }

    /**
     * 重新生成（保留原记录，覆盖内容与行程）
     */
    public Long regenerate(Long userId, Long planId) {
        TravelPlan plan = getOwned(userId, planId);
        PlanGenerateRequest req = new PlanGenerateRequest();
        req.setOrigin(plan.getOrigin());
        req.setDestination(plan.getDestination());
        req.setDays(plan.getDays());
        req.setBudget(plan.getBudget());
        req.setHobbies(plan.getHobbies());
        req.setPreferences(plan.getPreferences());
        GenMetric metric = new GenMetric();
        Itinerary itinerary = pipeline.run(req, metric);

        plan.setTitle(itinerary.getTitle());
        plan.setSummary(itinerary.getSummary());
        plan.setItineraryJson(toJson(itinerary));
        plan.setUpdateTime(LocalDateTime.now());
        planMapper.updateById(plan);
        metric.planId = plan.getId();
        logMetric(metric);
        return plan.getId();
    }

    /**
     * 分页查询当前用户的历史方案
     */
    public IPage<PlanVO> page(Long userId, int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<TravelPlan> wrapper = new LambdaQueryWrapper<TravelPlan>()
                .eq(TravelPlan::getUserId, userId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(TravelPlan::getTitle, keyword)
                    .or().like(TravelPlan::getDestination, keyword));
        }
        wrapper.orderByDesc(TravelPlan::getCreateTime);
        IPage<TravelPlan> page = planMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return page.convert(p -> PlanVO.from(p, parse(p.getItineraryJson())));
    }

    public PlanVO detail(Long userId, Long planId) {
        TravelPlan plan = getOwned(userId, planId);
        return PlanVO.from(plan, parse(plan.getItineraryJson()));
    }

    public void update(Long userId, Long planId, PlanUpdateRequest req) {
        TravelPlan plan = getOwned(userId, planId);
        plan.setTitle(req.getTitle());
        if (req.getSummary() != null) {
            plan.setSummary(req.getSummary());
        }
        if (req.getItinerary() != null) {
            Itinerary itinerary = req.getItinerary();
            // 酒店价格/预算只在缺失时补默认值，用户已调整的住宿预算与选定酒店原样保留
            standard.initStayPlan(itinerary, plan.getDays() == null ? 0 : plan.getDays());
            if (StringUtils.hasText(itinerary.getTitle()) && !itinerary.getTitle().equals(req.getTitle())) {
                itinerary.setTitle(req.getTitle());
            }
            plan.setItineraryJson(toJson(itinerary));
            if (itinerary.getDays() != null) {
                plan.setDays(itinerary.getDays().size());
            }
            if (!StringUtils.hasText(plan.getSummary()) && StringUtils.hasText(itinerary.getSummary())) {
                plan.setSummary(itinerary.getSummary());
            }
        }
        plan.setUpdateTime(LocalDateTime.now());
        planMapper.updateById(plan);
    }

    public void remove(Long userId, Long planId) {
        getOwned(userId, planId);
        planMapper.deleteById(planId);
    }

    /**
     * 统计方案数量（首页概览）
     */
    public long countByUser(Long userId) {
        Long count = planMapper.selectCount(
                new LambdaQueryWrapper<TravelPlan>().eq(TravelPlan::getUserId, userId));
        return count == null ? 0 : count;
    }

    public List<TravelPlan> latest(Long userId, int limit) {
        Page<TravelPlan> page = new Page<>(1, limit);
        LambdaQueryWrapper<TravelPlan> wrapper = new LambdaQueryWrapper<TravelPlan>()
                .eq(TravelPlan::getUserId, userId)
                .orderByDesc(TravelPlan::getCreateTime);
        return planMapper.selectPage(page, wrapper).getRecords();
    }

    /**
     * 校验归属，避免越权访问
     */
    private TravelPlan getOwned(Long userId, Long planId) {
        TravelPlan plan = planMapper.selectById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BusinessException(404, "方案不存在或无权访问");
        }
        return plan;
    }

    private String toJson(Itinerary itinerary) {
        try {
            return objectMapper.writeValueAsString(itinerary);
        } catch (Exception e) {
            throw new BusinessException("行程序列化失败：" + e.getMessage());
        }
    }

    private Itinerary parse(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Itinerary.class);
        } catch (Exception e) {
            log.warn("行程 JSON 解析失败：{}", e.getMessage());
            return null;
        }
    }
}
