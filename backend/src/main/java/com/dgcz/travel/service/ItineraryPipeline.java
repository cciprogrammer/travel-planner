package com.dgcz.travel.service;

import com.dgcz.travel.common.BusinessException;
import com.dgcz.travel.config.DeepSeekProperties;
import com.dgcz.travel.dto.Itinerary;
import com.dgcz.travel.dto.PlanGenerateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 行程质量三级处理链路：L1 自动校验 → L2 定向重写 → L3 降级兜底。
 *
 * <p>设计原则（也是优先级）：</p>
 * <ol>
 *   <li><b>能机器改的不花模型调用</b>：R3 时间乱序、R9 天数不符、R8 酒店分类缺失先在 L1 就地修复，
 *       修完立刻复检，修好了就不进 L2。</li>
 *   <li><b>机器补不出来的才重写</b>：R1/R4/R5/R6/R7/R11 属于内容缺失，只能带着「规则要求 + 修正方式 + 出错位置」
 *       的定向指令重写一次；重写结果只有在阻断项<b>严格更少</b>时才采纳，否则保留原结果。</li>
 *   <li><b>重写仍不达标才兜底</b>：调用内置兜底方案并复检，同样只在阻断项更少时采用；
 *       兜底方案自身也可能不达标（通用模板用占位词），那时如实标注、不假装通过。</li>
 *   <li><b>警告永不升级</b>：R2、R10 只记录，不触发重写，也不触发兜底。</li>
 * </ol>
 *
 * <p>每个阶段的触发依据（命中了哪些规则、处理前后阻断项数、产出是否被采纳）都写进
 * {@link Itinerary.Quality#getStages()}，任何一次生成都能回放走了哪条路径。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItineraryPipeline {

    private static final String L1 = "L1";
    private static final String L2 = "L2";
    private static final String L3 = "L3";

    private final DeepSeekService deepSeekService;
    private final ItineraryStandard standard;
    private final DeepSeekProperties deepSeekProps;

    public Itinerary run(PlanGenerateRequest req) {
        return run(req, new GenMetric());
    }

    /**
     * 执行三级链路。
     *
     * @param m 埋点载体，方法结束后其字段即为本次生成的完整统计
     */
    public Itinerary run(PlanGenerateRequest req, GenMetric m) {
        long t0 = System.currentTimeMillis();
        List<Itinerary.Stage> stages = new ArrayList<>();
        boolean fromLlm = deepSeekService.llmEnabled();
        m.fromLlm = fromLlm;
        m.destination = req.getDestination();
        m.days = req.getDays();

        Itinerary it;
        String source;
        if (!fromLlm) {
            it = deepSeekService.generateItinerary(req);
            source = "内置示例";
            m.degraded = true;
        } else {
            long c0 = System.currentTimeMillis();
            m.llmCalls++;
            try {
                it = deepSeekService.generateItinerary(req);
                source = "大模型";
            } catch (BusinessException e) {
                if (!Boolean.TRUE.equals(deepSeekProps.getFallbackToMockOnError())) {
                    throw e;
                }
                log.warn("大模型不可用，降级为内置示例数据：{}", e.getMessage());
                it = deepSeekService.fallbackMock(req);
                source = "内置示例（大模型不可用：" + e.getMessage() + "）";
                m.degraded = true;
            } finally {
                // 失败的调用同样计入耗时，否则「平均耗时」会漏掉超时与解析失败的那部分
                m.llmMs += System.currentTimeMillis() - c0;
            }
        }

        // 解析酒店每晚参考价并补齐住宿预算默认值（只补缺、不覆盖，供前端实时核算）
        standard.initStayPlan(it, req);

        /* ============================ L1 自动校验 ============================ */
        Itinerary.Quality q = standard.check(it, req);
        m.firstPassed = Boolean.TRUE.equals(q.getPassed());
        m.firstIssues = nz(q.getIssueCount());
        m.firstScore = nz(q.getScore());
        m.afterRepairIssues = m.firstIssues;
        stages.add(stage(L1, "自动校验",
                m.firstPassed ? "未触发升级：11 条规则全部通过" : "阻断项：" + ruleIds(q),
                null, q.getIssueCount(), true, null));

        if (!m.firstPassed) {
            List<String> auto = standard.autoFixableRules(q);
            int before = q.getIssueCount();
            if (auto.isEmpty()) {
                stages.add(stage(L1, "规则自动修复",
                        "未触发：阻断项 " + ruleIds(q) + " 均不属可机器修复类型",
                        before, before, false, "交由 L2 处理"));
            } else {
                boolean changed = standard.repair(it, req);
                Itinerary.Quality q1 = standard.check(it, req);
                boolean improved = q1.getIssueCount() < before;
                if (improved) {
                    q = q1;
                }
                m.repaired = changed;
                m.afterRepairIssues = q.getIssueCount();
                stages.add(stage(L1, "规则自动修复",
                        "命中可机修规则：" + String.join(",", auto) + (changed ? "" : "（未产生实际改动）"),
                        before, q.getIssueCount(), improved,
                        improved ? null : "修复后阻断项未减少"));
            }
            m.afterRepairIssues = q.getIssueCount();
        }

        /* ============================ L2 定向重写 ============================ */
        boolean rewroteAdopted = false;
        if (!Boolean.TRUE.equals(q.getPassed())) {
            List<String> need = standard.rewriteRules(q);
            int before = q.getIssueCount();
            if (need.isEmpty()) {
                stages.add(stage(L2, "定向重写",
                        "未触发：剩余阻断项均无可重写的规则（仅警告或已修复）",
                        before, before, false, "转 L3"));
            } else if (!fromLlm) {
                stages.add(stage(L2, "定向重写",
                        "未触发：当前数据来源为内置示例，无重写通道",
                        before, before, false, "转 L3"));
            } else {
                m.llmCalls++;
                long c1 = System.currentTimeMillis();
                try {
                    Itinerary retry = deepSeekService.generateItinerary(req, standard.buildCorrection(it, req, q));
                    m.rewrote = true;
                    standard.initStayPlan(retry, req);
                    standard.repair(retry, req);
                    Itinerary.Quality q2 = standard.check(retry, req);
                    m.afterRewriteIssues = q2.getIssueCount();
                    boolean adopt = q2.getIssueCount() < before;
                    if (adopt) {
                        it = retry;
                        q = q2;
                        source = "大模型（定向重写后）";
                        rewroteAdopted = true;
                    }
                    stages.add(stage(L2, "定向重写",
                            "命中需重写规则：" + String.join(",", need) + "｜纠正指令 " + need.size() + " 组",
                            before, q2.getIssueCount(), adopt,
                            adopt ? null : "重写结果未更优（" + q2.getIssueCount() + " ≥ " + before + "），保留首轮结果"));
                } catch (Exception e) {
                    stages.add(stage(L2, "定向重写",
                            "命中需重写规则：" + String.join(",", need),
                            before, before, false, "重写调用失败，保留首轮结果：" + brief(e)));
                } finally {
                    m.llmMs += System.currentTimeMillis() - c1;
                }
            }
        }

        /* ============================ L3 降级兜底 ============================ */
        boolean fallbackAdopted = false;
        if (!Boolean.TRUE.equals(q.getPassed())) {
            int before = q.getIssueCount();
            String trigger = rewroteAdopted
                    ? "重写后仍不达标：阻断项 " + q.getIssueCount() + " 条"
                    : (m.rewrote ? "重写结果未更优，仍不达标" : "无可用重写通道，直接兜底");
            try {
                Itinerary fb = deepSeekService.fallbackMock(req);
                standard.initStayPlan(fb, req);
                Itinerary.Quality qf = standard.check(fb, req);
                m.fellBack = true;
                m.afterFallbackIssues = qf.getIssueCount();
                boolean adopt = qf.getIssueCount() < before;
                if (adopt) {
                    it = fb;
                    q = qf;
                    source = "兜底示例（原方案 " + before + " 项阻断未达标）";
                    fallbackAdopted = true;
                }
                stages.add(stage(L3, "降级兜底", trigger,
                        before, qf.getIssueCount(), adopt,
                        adopt ? "兜底方案阻断项 " + qf.getIssueCount() + " 条，已采用"
                                : "兜底方案未更优（" + qf.getIssueCount() + " ≥ " + before + "），保留原结果并标注不达标"));
            } catch (Exception e) {
                stages.add(stage(L3, "降级兜底", trigger, before, before, false,
                        "兜底资源不可用：" + brief(e)));
            }
        }

        q.setSource(source);
        q.setStages(stages);
        it.setQuality(q);
        m.finalPassed = Boolean.TRUE.equals(q.getPassed());
        m.finalIssues = nz(q.getIssueCount());
        m.finalScore = nz(q.getScore());
        m.source = source;
        m.stageReached = resolveStage(m, rewroteAdopted, fallbackAdopted);
        m.totalMs = System.currentTimeMillis() - t0;
        if (!m.finalPassed) {
            log.warn("行程未完全达标，阻断项 {} 条，来源：{}，路径：{}", q.getIssueCount(), source, m.stageReached);
        }
        return it;
    }

    /** 本次生成最终走到哪一级：用于统计「各兜底层的占比」 */
    private String resolveStage(GenMetric m, boolean rewroteAdopted, boolean fallbackAdopted) {
        if (m.firstPassed) {
            return "L1-直接通过";
        }
        if (rewroteAdopted && m.finalPassed) {
            return "L2-重写后通过";
        }
        if (fallbackAdopted) {
            return m.finalPassed ? "L3-兜底后通过" : "L3-兜底仍未通过";
        }
        if (m.finalPassed) {
            return "L1-修复后通过";
        }
        return "未达标-已标注";
    }

    private Itinerary.Stage stage(String stage, String action, String trigger,
                                  Integer before, Integer after, boolean adopted, String note) {
        Itinerary.Stage s = new Itinerary.Stage();
        s.setStage(stage);
        s.setAction(action);
        s.setTrigger(trigger);
        s.setIssuesBefore(before);
        s.setIssuesAfter(after);
        s.setAdopted(adopted);
        s.setNote(note);
        return s;
    }

    /** 阻断项涉及的规则编号，去重保序 */
    private String ruleIds(Itinerary.Quality q) {
        if (q == null || q.getIssues() == null) {
            return "无";
        }
        List<String> ids = q.getIssues().stream()
                .filter(i -> "阻断".equals(i.getLevel()))
                .map(Itinerary.Issue::getRule)
                .distinct()
                .toList();
        return ids.isEmpty() ? "无" : String.join(",", ids);
    }

    private String brief(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return msg.length() > 90 ? msg.substring(0, 90) + "…" : msg;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
