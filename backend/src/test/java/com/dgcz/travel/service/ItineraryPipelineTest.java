package com.dgcz.travel.service;

import com.dgcz.travel.config.DeepSeekProperties;
import com.dgcz.travel.dto.Itinerary;
import com.dgcz.travel.dto.PlanGenerateRequest;
import com.dgcz.travel.dto.RuleSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 三级处理链路（L1 自动校验 → L2 定向重写 → L3 降级兜底）的行为验证。
 *
 * <p>覆盖 11 条规则 × 三类典型场景（通过 / 需重写 / 需兜底）共 33 个用例：
 * 每条规则先验证「合格数据 → 直接通过」，再验证「违规数据 → 按规则归属的处理方式走对应层级」。
 * 其中 R2、R10 为警告项，按设计永不升级；R3、R9 由 L1 自动修复闭环，不进入 L2/L3，
 * 这 6 个格子同样以断言的方式固化下来，避免以后改坏优先级。</p>
 */
class ItineraryPipelineTest {

    private final ItineraryStandard standard = new ItineraryStandard();
    private final DeepSeekService ds = mock(DeepSeekService.class);
    private final DeepSeekProperties props = new DeepSeekProperties();
    private final ItineraryPipeline pipeline;

    ItineraryPipelineTest() {
        props.setFallbackToMockOnError(true);
        pipeline = new ItineraryPipeline(ds, standard, props);
        when(ds.llmEnabled()).thenReturn(true);
    }

    /* ============================ 33 个场景的用例表 ============================ */

    static Stream<Object[]> cases() {
        List<Object[]> list = new ArrayList<>();
        String[] blockingRewrite = {"R1", "R4", "R5", "R6", "R7", "R8", "R11"};
        for (String rule : blockingRewrite) {
            list.add(new Object[]{rule, "PASS", "L1 直接通过"});
            list.add(new Object[]{rule, "REWRITE", "L2 定向重写后通过"});
            list.add(new Object[]{rule, "FALLBACK", "L3 降级兜底后通过"});
        }
        for (String rule : new String[]{"R3", "R9"}) {
            list.add(new Object[]{rule, "PASS", "L1 直接通过"});
            list.add(new Object[]{rule, "REWRITE", "L1 自动修复后通过"});
            list.add(new Object[]{rule, "FALLBACK", "L1 自动修复后通过"});
        }
        for (String rule : new String[]{"R2", "R10"}) {
            list.add(new Object[]{rule, "PASS", "L1 直接通过"});
            list.add(new Object[]{rule, "REWRITE", "警告不升级"});
            list.add(new Object[]{rule, "FALLBACK", "警告不升级"});
        }
        return list.stream();
    }

    @ParameterizedTest(name = "{0} · {1} → {2}")
    @MethodSource("cases")
    @DisplayName("三级链路：11 条规则 × 通过/需重写/需兜底")
    void pipelineRoutesByRule(String rule, String scenario, String expect) {
        PlanGenerateRequest req = req();
        Itinerary bad = violation(rule);
        Itinerary good = good();

        switch (scenario) {
            case "PASS" -> when(ds.generateItinerary(any(PlanGenerateRequest.class))).thenReturn(good);
            case "REWRITE" -> {
                when(ds.generateItinerary(any(PlanGenerateRequest.class))).thenReturn(bad);
                when(ds.generateItinerary(any(PlanGenerateRequest.class), anyString())).thenReturn(good);
                when(ds.fallbackMock(any(PlanGenerateRequest.class))).thenReturn(good);
            }
            default -> {
                when(ds.generateItinerary(any(PlanGenerateRequest.class))).thenReturn(bad);
                when(ds.generateItinerary(any(PlanGenerateRequest.class), anyString())).thenReturn(bad);
                when(ds.fallbackMock(any(PlanGenerateRequest.class))).thenReturn(good);
            }
        }

        GenMetric m = new GenMetric();
        Itinerary out = pipeline.run(req, m);
        Itinerary.Quality q = out.getQuality();

        assertNotNull(q, "必须返回质检结果");
        assertNotNull(q.getStages(), "必须留下三级链路的阶段轨迹");
        assertEquals("L1", q.getStages().get(0).getStage(), "链路必须从 L1 开始");

        switch (expect) {
            case "L1 直接通过" -> {
                assertTrue(q.getPassed(), rule + "：合格数据应直接通过");
                assertEquals("L1-直接通过", m.stageReached);
                assertFalse(hasAdoptedStage(q, "定向重写"), "不应触发重写");
                assertFalse(hasAdoptedStage(q, "降级兜底"), "不应触发兜底");
                assertTrue(q.getStages().get(0).getTrigger().contains("全部通过"));
            }
            case "L2 定向重写后通过" -> {
                assertTrue(q.getPassed(), rule + "：重写后应达标");
                assertEquals("L2-重写后通过", m.stageReached);
                assertTrue(hasAdoptedStage(q, "定向重写"), rule + "：应触发并采纳定向重写");
                assertTrue(q.getSource().contains("定向重写"), "来源需标注为重写后");
                assertFalse(hasAdoptedStage(q, "降级兜底"), "重写已解决，不应再兜底");
                assertTrue(m.rewrote, "必须发生过一次重写");
            }
            case "L3 降级兜底后通过" -> {
                assertTrue(q.getPassed(), rule + "：兜底后应达标");
                assertEquals("L3-兜底后通过", m.stageReached);
                assertNotNull(stageBy(q, "定向重写"), "应先尝试重写");
                assertTrue(hasAdoptedStage(q, "降级兜底"), rule + "：重写无效时应触发兜底");
                assertTrue(q.getSource().contains("兜底示例"), "来源需标注为兜底");
                assertEquals(2, m.llmCalls, "首轮 + 重写共两次大模型调用");
            }
            case "L1 自动修复后通过" -> {
                assertTrue(q.getPassed(), rule + "：机器修复后应达标");
                assertEquals("L1-修复后通过", m.stageReached);
                assertTrue(hasAdoptedStage(q, "规则自动修复"), rule + "：应走 L1 自动修复");
                assertFalse(m.rewrote, rule + "：能被机器修复的不应消耗大模型重写");
                assertFalse(m.fellBack, "不应触发兜底");
            }
            default -> {
                assertTrue(q.getPassed(), rule + "：警告不阻断通过");
                assertTrue(q.getIssues().stream().anyMatch(i -> rule.equals(i.getRule()) && "警告".equals(i.getLevel())),
                        rule + "：警告项应被记录");
                assertFalse(m.rewrote, rule + "：警告不得触发重写");
                assertFalse(m.fellBack, rule + "：警告不得触发兜底");
            }
        }
    }

    /* ============================ 链路自身的行为约束 ============================ */

    @Test
    @DisplayName("重写结果未更优时不采纳，保留原结果并进入兜底")
    void rewriteNotBetterIsRejected() {
        Itinerary bad = violation("R4");
        Itinerary worse = violation("R4");
        worse.getDays().get(0).setTransport(null);          // 再多一条 R5 阻断
        Itinerary fb = good();
        when(ds.generateItinerary(any(PlanGenerateRequest.class))).thenReturn(bad);
        when(ds.generateItinerary(any(PlanGenerateRequest.class), anyString())).thenReturn(worse);
        when(ds.fallbackMock(any(PlanGenerateRequest.class))).thenReturn(fb);

        GenMetric m = new GenMetric();
        Itinerary out = pipeline.run(req(), m);
        Itinerary.Quality q = out.getQuality();

        assertTrue(q.getPassed());
        Itinerary.Stage l2 = stageBy(q, "定向重写");
        assertNotNull(l2);
        assertFalse(l2.getAdopted(), "更差的结果不得采纳");
        assertTrue(l2.getNote().contains("未更优"));
        assertEquals("L3-兜底后通过", m.stageReached);
    }

    @Test
    @DisplayName("兜底方案同样不达标时不假装通过，如实标注")
    void fallbackAlsoBadStaysFailed() {
        Itinerary bad = violation("R1");
        Itinerary badMock = violation("R1");
        badMock.getDays().get(1).getItems().get(0).setPlace("待定");   // 兜底更差
        when(ds.generateItinerary(any(PlanGenerateRequest.class))).thenReturn(bad);
        when(ds.generateItinerary(any(PlanGenerateRequest.class), anyString())).thenReturn(bad);
        when(ds.fallbackMock(any(PlanGenerateRequest.class))).thenReturn(badMock);

        GenMetric m = new GenMetric();
        Itinerary out = pipeline.run(req(), m);
        Itinerary.Quality q = out.getQuality();

        assertFalse(q.getPassed(), "兜底不达标时必须如实标记未通过");
        assertEquals("未达标-已标注", m.stageReached);
        assertTrue(q.getIssueCount() > 0);
        Itinerary.Stage l3 = stageBy(q, "降级兜底");
        assertNotNull(l3);
        assertFalse(l3.getAdopted(), "兜底未更优不应采纳");
    }

    @Test
    @DisplayName("定向纠正指令按规则分组输出，包含要求、修正方式与具体位置")
    void correctionIsTargeted() {
        Itinerary bad = violation("R1");
        bad.getDays().get(0).getItems().get(1).setTransport(null);
        PlanGenerateRequest req = req();
        Itinerary.Quality q = standard.check(bad, req);

        String correction = standard.buildCorrection(bad, req, q);

        assertTrue(correction.contains("[R1 地点具体性] 必须满足："));
        assertTrue(correction.contains("修正方式：把泛称或占位地点替换为"));
        assertTrue(correction.contains("[R4 点对点交通]"));
        assertTrue(correction.contains("第 1 天 · 第 2 项"), "要能定位到具体位置");
        assertFalse(correction.contains("R2"), "警告项不应进入纠正指令");
    }

    @Test
    @DisplayName("规则清单固定 11 条，且编号与处理方式齐全")
    void ruleSpecsAreComplete() {
        List<RuleSpec> rules = standard.rules();
        assertEquals(11, rules.size(), "规则条数必须与达标率口径一致");
        assertEquals(Arrays.asList("R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8", "R9", "R10", "R11"),
                rules.stream().map(RuleSpec::getRule).toList());
        assertTrue(rules.stream().filter(RuleSpec::isBlocking).count() >= 9, "阻断规则数量");
        for (RuleSpec r : rules) {
            if (r.isBlocking()) {
                assertTrue(r.isAutoFixable() || r.needsRewrite(), r.getRule() + " 必须绑定至少一条处理路径");
            } else {
                assertFalse(r.isAutoFixable() || r.needsRewrite(), r.getRule() + " 是警告项，不应绑定升级处理");
            }
        }
    }

    /* ================================ 测试数据构造 ================================ */

    private PlanGenerateRequest req() {
        PlanGenerateRequest req = new PlanGenerateRequest();
        req.setOrigin("东莞");
        req.setDestination("测试城");
        req.setDays(2);
        req.setBudget(new BigDecimal("3000"));
        return req;
    }

    /** 完全满足 11 条规则的基准行程 */
    private Itinerary good() {
        Itinerary it = new Itinerary();
        it.setTitle("测试城两日游");
        it.setSummary("基准合格方案");

        Itinerary.DayPlan d1 = day(1, "09:00", "12:00", "15:00");
        Itinerary.DayPlan d2 = day(2, "10:00", "13:00", "16:00");
        it.setDays(new ArrayList<>(List.of(d1, d2)));

        Itinerary.Transport t = new Itinerary.Transport();
        t.setOutbound("东莞站乘高铁至测试城站，约 2 小时");
        t.setInbound("测试城站乘高铁返东莞站，约 2 小时");
        t.setLocal("市内以地铁为主、网约车为辅");
        t.setOptions(new ArrayList<>(List.of("地铁 2 元起", "网约车 15 元起")));
        it.setTransport(t);

        List<Itinerary.Hotel> hotels = new ArrayList<>();
        hotels.add(hotel("城市中心酒店", "350-400 元/晚", "推荐", "舒适型"));
        hotels.add(hotel("滨江商务酒店", "220-260 元/晚", "备选", "经济型"));
        hotels.add(hotel("云顶度假酒店", "700-800 元/晚", "备选", "高档型"));
        it.setHotels(hotels);

        Itinerary.StayPlan sp = new Itinerary.StayPlan();
        sp.setBudgetPerNight(400);
        sp.setNights(1);
        sp.setSelectedHotel("城市中心酒店");
        it.setStay(sp);

        List<Itinerary.ShoppingSpot> shops = new ArrayList<>();
        shops.add(shop("中心步行街", "服饰美妆"));
        shops.add(shop("老城特产市场", "特产伴手礼"));
        it.setShopping(shops);

        Itinerary.BudgetBreakdown b = new Itinerary.BudgetBreakdown();
        b.setTransport("600");
        b.setAccommodation("400");
        b.setFood("600");
        b.setTickets("300");
        b.setOther("200");
        b.setTotal("2100");
        it.setBudget(b);
        it.setTips(new ArrayList<>(List.of("提前购票")));
        return it;
    }

    /** 按规则构造一份只违反该规则的行程 */
    private Itinerary violation(String rule) {
        Itinerary it = good();
        switch (rule) {
            case "R1" -> it.getDays().get(0).getItems().get(1).setPlace("核心景区");
            case "R2" -> it.getDays().get(0).getItems().remove(2);
            case "R3" -> {
                Itinerary.Item a = it.getDays().get(0).getItems().get(0);
                Itinerary.Item b = it.getDays().get(0).getItems().get(1);
                a.setTime("18:00");
                b.setTime("09:00");
            }
            case "R4" -> it.getDays().get(0).getItems().get(1).setTransport(null);
            case "R5" -> it.getDays().get(0).setTransport(null);
            case "R6" -> it.getTransport().setOutbound(null);
            case "R7" -> it.getTransport().setLocal(null);
            case "R8" -> {
                it.getHotels().get(0).setPrice("900-1000 元/晚");
                it.getHotels().get(1).setPrice("1200-1300 元/晚");
                it.getHotels().get(2).setPrice("1500-1800 元/晚");
            }
            case "R9" -> it.getDays().add(day(3, "09:00", "12:00", "15:00"));
            case "R10" -> it.getBudget().setTotal(null);
            case "R11" -> it.getShopping().remove(1);
            default -> throw new IllegalArgumentException("未收录的规则：" + rule);
        }
        return it;
    }

    private Itinerary.DayPlan day(int n, String t1, String t2, String t3) {
        Itinerary.DayPlan d = new Itinerary.DayPlan();
        d.setDay(n);
        d.setDate("第 " + n + " 天");
        d.setTheme("城市漫游");
        d.setTransport("当天以地铁串联，单程约 20 分钟");
        List<Itinerary.Item> items = new ArrayList<>();
        items.add(item(t1, "中央公园", "散步与拍照"));
        items.add(item(t2, "市博物馆", "看常设展"));
        items.add(item(t3, "老城步行街", "晚餐与逛街"));
        d.setItems(items);
        return d;
    }

    private Itinerary.Item item(String time, String place, String activity) {
        Itinerary.Item i = new Itinerary.Item();
        i.setTime(time);
        i.setPlace(place);
        i.setActivity(activity);
        i.setTransport("地铁约 20 分钟");
        i.setCost("50");
        i.setTips("避开高峰");
        return i;
    }

    private Itinerary.Hotel hotel(String name, String price, String category, String level) {
        Itinerary.Hotel h = new Itinerary.Hotel();
        h.setName(name);
        h.setArea("市中心");
        h.setPrice(price);
        h.setReason("交通便利");
        h.setAdvantage("步行可达地铁站");
        h.setCategory(category);
        h.setLevel(level);
        return h;
    }

    private Itinerary.ShoppingSpot shop(String name, String type) {
        Itinerary.ShoppingSpot s = new Itinerary.ShoppingSpot();
        s.setName(name);
        s.setArea("中心城区");
        s.setType(type);
        s.setBuyWhat("本地特产与伴手礼");
        s.setSuitableFor("家庭出行");
        return s;
    }

    private boolean hasAdoptedStage(Itinerary.Quality q, String action) {
        Itinerary.Stage s = stageBy(q, action);
        return s != null && Boolean.TRUE.equals(s.getAdopted());
    }

    private Itinerary.Stage stageBy(Itinerary.Quality q, String action) {
        return q.getStages().stream().filter(s -> action.equals(s.getAction())).findFirst().orElse(null);
    }
}
