package com.dgcz.travel.service;

import com.dgcz.travel.dto.Itinerary;
import com.dgcz.travel.dto.PlanGenerateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 白盒测试 P0-1：行程质量标准 R1-R11 的判定与自动修复。
 *
 * 这些是纯函数（不依赖 Spring 容器与数据库），直接 new 出来就能测，
 * 是最该有回归保护的部分——改规则时先跑它。
 */
class ItineraryStandardTest {

    private final ItineraryStandard standard = new ItineraryStandard();

    /* ------------------------------ 构造工具 ------------------------------ */

    private Itinerary.Item item(String time, String place) {
        Itinerary.Item it = new Itinerary.Item();
        it.setTime(time);
        it.setPlace(place);
        it.setActivity("游览 " + place);
        it.setTransport("步行约 10 分钟");
        it.setCost("免费");
        return it;
    }

    private Itinerary.DayPlan day(int no) {
        Itinerary.DayPlan d = new Itinerary.DayPlan();
        d.setDay(no);
        d.setTheme("第 " + no + " 天");
        d.setTransport("当天以地铁 + 步行为主");
        List<Itinerary.Item> items = new ArrayList<>();
        items.add(item("09:00-11:00", "橘子洲头景区"));
        items.add(item("11:30-13:00", "坡子街火宫殿总店"));
        items.add(item("14:00-16:30", "岳麓书院"));
        d.setItems(items);
        return d;
    }

    private Itinerary.Hotel hotel(String name, int pricePerNight, String category) {
        Itinerary.Hotel h = new Itinerary.Hotel();
        h.setName(name);
        h.setArea("五一广场");
        h.setPrice(pricePerNight + " 元/晚");
        h.setPricePerNight(pricePerNight);
        h.setReason("位置方便");
        h.setAdvantage("近地铁");
        h.setLevel("舒适型");
        h.setCategory(category);
        return h;
    }

    private Itinerary.ShoppingSpot shop(String name) {
        Itinerary.ShoppingSpot s = new Itinerary.ShoppingSpot();
        s.setName(name);
        s.setArea("五一广场商圈");
        s.setType("特产伴手礼");
        s.setBuyWhat("臭豆腐真空装");
        return s;
    }

    /** 一份 2 天、11 条规则全部满足的行程 */
    private Itinerary valid() {
        Itinerary it = new Itinerary();
        it.setTitle("长沙两日");
        it.setSummary("测试用行程");
        List<Itinerary.DayPlan> days = new ArrayList<>();
        days.add(day(1));
        days.add(day(2));
        it.setDays(days);

        Itinerary.Transport t = new Itinerary.Transport();
        t.setOutbound("广州南站乘高铁 G1102 次前往长沙南站");
        t.setInbound("长沙南站乘高铁 G1103 次返回广州南站");
        t.setLocal("市内以地铁 2 号线为主，扫码进站");
        t.setOptions(List.of("高铁：约 2.5 小时", "普速列车：约 8 小时"));
        it.setTransport(t);

        it.setHotels(List.of(
                hotel("全季酒店", 460, "推荐"),
                hotel("汉庭酒店", 260, "备选"),
                hotel("尼依格罗酒店", 1050, "备选")));

        Itinerary.StayPlan stay = new Itinerary.StayPlan();
        stay.setBudgetPerNight(500);
        stay.setNights(1);
        stay.setSelectedHotel("全季酒店");
        it.setStay(stay);

        it.setShopping(List.of(shop("太平老街"), shop("黄兴路步行街")));

        Itinerary.BudgetBreakdown b = new Itinerary.BudgetBreakdown();
        b.setTransport("约 700 元");
        b.setAccommodation("约 460 元");
        b.setFood("约 300 元");
        b.setTickets("约 100 元");
        b.setOther("约 100 元");
        b.setTotal("约 1660 元");
        it.setBudget(b);

        it.setTips(List.of("周一省博物馆闭馆"));
        return it;
    }

    private PlanGenerateRequest req(int days) {
        PlanGenerateRequest r = new PlanGenerateRequest();
        r.setOrigin("广州");
        r.setDestination("长沙");
        r.setDays(days);
        return r;
    }

    private List<String> rules(Itinerary.Quality q) {
        return q.getIssues().stream().map(Itinerary.Issue::getRule).toList();
    }

    /* ------------------------------ 正常路径 ------------------------------ */

    @Test
    @DisplayName("完整合规行程 → 通过、达标率 100、0 条阻断")
    void validItineraryPasses() {
        Itinerary.Quality q = standard.check(valid(), req(2));
        assertTrue(q.getPassed(), "应判定通过，实际未通过：" + rules(q));
        assertEquals(0, q.getIssueCount());
        assertEquals(100, q.getScore());
        assertTrue(q.getIssues().isEmpty());
    }

    @Test
    @DisplayName("null 行程 → R0 阻断，不抛异常")
    void nullItineraryGivesR0() {
        Itinerary.Quality q = standard.check(null, req(2));
        assertFalse(q.getPassed());
        assertEquals(List.of("R0"), rules(q));
    }

    /* ------------------------------ 逆向用例 ------------------------------ */

    @Test
    @DisplayName("缺少每日交通小结 → R5 阻断")
    void missingDayTransportGivesR5() {
        Itinerary it = valid();
        it.getDays().get(0).setTransport(null);
        Itinerary.Quality q = standard.check(it, req(2));
        assertTrue(rules(q).contains("R5"));
        assertFalse(q.getPassed());
    }

    @Test
    @DisplayName("缺少点对点交通 → R4 阻断")
    void missingItemTransportGivesR4() {
        Itinerary it = valid();
        it.getDays().get(1).getItems().get(2).setTransport(" ");
        Itinerary.Quality q = standard.check(it, req(2));
        assertTrue(rules(q).contains("R4"));
    }

    @Test
    @DisplayName("天数与请求不一致 → R9 阻断")
    void dayCountMismatchGivesR9() {
        Itinerary it = valid();
        Itinerary.Quality q = standard.check(it, req(3));
        assertTrue(rules(q).contains("R9"));
    }

    @Test
    @DisplayName("购物点不足 2 个 → R11 阻断")
    void fewShoppingSpotsGivesR11() {
        Itinerary it = valid();
        it.setShopping(List.of(shop("太平老街")));
        Itinerary.Quality q = standard.check(it, req(2));
        assertTrue(rules(q).contains("R11"));
    }

    @Test
    @DisplayName("酒店不足 3 家 → R8 阻断")
    void fewHotelsGivesR8() {
        Itinerary it = valid();
        it.setHotels(List.of(hotel("全季酒店", 460, "推荐"), hotel("汉庭酒店", 260, "备选")));
        Itinerary.Quality q = standard.check(it, req(2));
        assertTrue(rules(q).contains("R8"));
        assertTrue(q.getIssues().stream()
                .anyMatch(i -> i.getDetail().contains("至少 3 家")));
    }

    @Test
    @DisplayName("住宿预算内没有任何酒店 → R8 住宿预算匹配 阻断")
    void noHotelWithinBudgetGivesR8() {
        Itinerary it = valid();
        it.getStay().setBudgetPerNight(100);
        Itinerary.Quality q = standard.check(it, req(2));
        assertTrue(q.getIssues().stream()
                .anyMatch(i -> "R8".equals(i.getRule()) && i.getDetail().contains("没有任何酒店落在每晚预算")));
    }

    @Test
    @DisplayName("往返交通缺失 → R6 阻断")
    void missingRoundTripGivesR6() {
        Itinerary it = valid();
        it.getTransport().setOutbound("");
        Itinerary.Quality q = standard.check(it, req(2));
        assertTrue(rules(q).contains("R6"));
    }

    @Test
    @DisplayName("市内交通缺失 → R7 阻断")
    void missingLocalTransportGivesR7() {
        Itinerary it = valid();
        it.getTransport().setLocal(null);
        Itinerary.Quality q = standard.check(it, req(2));
        assertTrue(rules(q).contains("R7"));
    }

    @Test
    @DisplayName("警告级问题不影响 passed，但会扣达标率")
    void warningsDoNotBlockButReduceScore() {
        Itinerary it = valid();
        // 删掉一个事项 → 只剩 2 个 → 触发 R2（警告）
        it.getDays().get(0).getItems().remove(2);
        Itinerary.Quality q = standard.check(it, req(2));
        assertTrue(q.getPassed(), "R2 是警告，不应判为阻断：" + rules(q));
        assertEquals(0, q.getIssueCount());
        // 11 条里失败 1 条 → round(10 / 11 * 100) = 91
        assertEquals(91, q.getScore());
    }

    /* ------------------------------ 自动修复 ------------------------------ */

    @Test
    @DisplayName("时间乱序 → R3 阻断；repair() 按开始时间重排后可修复")
    void repairFixesOutOfOrderTimes() {
        Itinerary it = valid();
        List<Itinerary.Item> items = it.getDays().get(0).getItems();
        items.get(0).setTime("15:00-16:00");
        items.get(1).setTime("10:00-11:00");
        items.get(2).setTime("12:00-13:00");

        Itinerary.Quality before = standard.check(it, req(2));
        assertTrue(rules(before).contains("R3"), "乱序应触发 R3");

        boolean changed = standard.repair(it, req(2));
        assertTrue(changed, "repair 应报告发生了修改");

        Itinerary.Quality after = standard.check(it, req(2));
        assertFalse(rules(after).contains("R3"), "修复后不应再有 R3：" + rules(after));
    }

    /* ------------------------------ 地点判定 ------------------------------ */

    @Test
    @DisplayName("具体地名通过、占位词拦截")
    void placeConcreteness() {
        assertTrue(standard.isPlaceConcrete("橘子洲头景区"));
        assertTrue(standard.isPlaceConcrete("坡子街火宫殿总店"));
        assertFalse(standard.isPlaceConcrete("待定"));
        assertFalse(standard.isPlaceConcrete("暂无景点"));
        assertFalse(standard.isPlaceConcrete("本地特色餐厅"));
        assertFalse(standard.isPlaceConcrete(""));
        assertFalse(standard.isPlaceConcrete("   "));
    }

    /**
     * 已知缺陷 D9 的现状锁定。
     * 两字真实核心景点（外滩 / 故宫 / 天坛 / 西湖 / 黄山 / 泰山）被 MIN_PLACE_LENGTH=3 误判为「不具体」。
     * 修复后这个用例应当反转成 assertTrue —— 到那时记得同步改这里。
     */
    @Test
    @DisplayName("已知缺陷 D9：两字真实地名当前被判不具体")
    void twoCharRealPlacesAreRejected_knownDefectD9() {
        assertFalse(standard.isPlaceConcrete("外滩"));
        assertFalse(standard.isPlaceConcrete("故宫"));
        assertFalse(standard.isPlaceConcrete("西湖"));
        // 三个字就能过，说明只是长度阈值问题，不是词表问题
        assertTrue(standard.isPlaceConcrete("外滩源"));
    }

    /* ------------------------------ 解析工具 ------------------------------ */

    @Test
    @DisplayName("parsePricePerNight：区间取中值、单值取原值、非法返回 null")
    void parsePricePerNight() {
        assertEquals(490, standard.parsePricePerNight("420-560 元/晚"));
        assertEquals(388, standard.parsePricePerNight("约 388 元/晚"));
        assertEquals(300, standard.parsePricePerNight("300"));
        assertNull(standard.parsePricePerNight("价格面议"));
        assertNull(standard.parsePricePerNight(null));
    }

    @Test
    @DisplayName("parseStartMinutes：区间取起始、非时间返回 -1")
    void parseStartMinutes() {
        assertEquals(540, standard.parseStartMinutes("09:00-11:30"));
        assertEquals(540, standard.parseStartMinutes("09:00"));
        assertEquals(575, standard.parseStartMinutes("9:35"));
        assertEquals(-1, standard.parseStartMinutes("早上"));
        assertEquals(-1, standard.parseStartMinutes(null));
        assertEquals(-1, standard.parseStartMinutes("25:00"));
    }
}
