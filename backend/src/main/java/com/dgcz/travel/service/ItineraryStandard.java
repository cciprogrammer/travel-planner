package com.dgcz.travel.service;

import com.dgcz.travel.dto.Itinerary;
import com.dgcz.travel.dto.PlanGenerateRequest;
import com.dgcz.travel.dto.RuleSpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 行程质量标准（全局统一）。
 *
 * <p>适用范围：本系统输出的<strong>所有</strong>旅行方案，不区分目的地城市，也不区分来源
 * （大模型生成 / 内置示例数据）都必须通过同一套校验。</p>
 *
 * <p>三类能力的对应关系：</p>
 * <ol>
 *   <li>{@link #check} —— 判定依据：给出逐条规则的通过/不通过结果；</li>
 *   <li>{@link #repair} —— 可自动修复项的兜底处理；</li>
 *   <li>{@link #buildCorrection} —— 无法自动修复时，生成给大模型的纠正指令（触发一次重写）。</li>
 * </ol>
 */
@Component
public class ItineraryStandard {

    /* ============================ 标准参数（判定阈值） ============================ */

    /** 每天至少安排的事项数 */
    public static final int MIN_ITEMS_PER_DAY = 3;

    /** 住宿推荐至少给出的酒店数量（含推荐 + 备选） */
    public static final int MIN_HOTELS = 3;

    /** 市内交通可选方式至少给出的条数 */
    public static final int MIN_TRANSPORT_OPTIONS = 2;

    /** 购物推荐至少给出的地点/区域数量（只推荐去处，不设任何额度） */
    public static final int MIN_SHOPPING_SPOTS = 2;

    /** 行程质量标准的总规则数，用于计算达标率 */
    private static final int TOTAL_RULES = 11;

    /**
     * 泛称词表：地点名中出现以下任一表述，即判定「地点不具体」。
     * 只收录必然属于占位表述的词，避免误伤“坡子街火宫殿”这类真实店名。
     */
    private static final String[] BANNED_PLACE_WORDS = {
            "核心景区", "地标景区", "代表性地标", "特色景区",
            "本地特色餐厅", "特色餐饮街", "本地餐饮", "特色小吃街",
            "文化街区", "夜景打卡地", "滨水步道", "打卡地",
            "待定", "暂无", "TBD"
    };

    /** 往返交通必须命中的交通方式关键词 */
    private static final String[] TRANSPORT_KEYWORDS = {
            "高铁", "动车", "火车", "列车", "航班", "飞机", "机场",
            "自驾", "大巴", "长途汽车", "轮渡", "磁浮", "地铁"
    };

    /** 地点名的最小长度 */
    private static final int MIN_PLACE_LENGTH = 3;

    /* ======================= 规则元数据（三级链路的触发依据） ======================= */

    /**
     * 11 条规则的完整定义，把「判定标准」和「不通过时走哪一级处理」绑定在一起：
     * <ul>
     *   <li>AUTO —— 可机器就地修复，命中后走 L1 的自动修复，不消耗大模型调用；</li>
     *   <li>MODEL —— 内容缺失/不合规，机器补不出来，只有走 L2 定向重写；</li>
     *   <li>AUTO+MODEL —— 先尝试机器修复，修不好再重写（如 R8 的酒店分类可补、缺酒店不可补）；</li>
     *   <li>RECORD —— 警告项，只记录不阻断，<strong>永不触发升级处理</strong>。</li>
     * </ul>
     */
    private static final List<RuleSpec> RULES = List.of(
            new RuleSpec("R1", "地点具体性", "阻断", "MODEL",
                    "每项写出真实具体的地点全称，不得使用泛称或占位词",
                    null,
                    "把泛称或占位地点替换为该城市真实存在的地点全称（景区/街区/博物馆/店名）"),
            new RuleSpec("R2", "每日事项数量", "警告", "RECORD",
                    "每天至少安排 3 个事项",
                    null, null),
            new RuleSpec("R3", "游览顺序", "阻断", "AUTO",
                    "当日事项按开始时间非递减排列",
                    "按开始时间重排当日事项", null),
            new RuleSpec("R4", "点对点交通", "阻断", "MODEL",
                    "每个事项写明从上一地点到达此处的交通方式与耗时",
                    null,
                    "为缺少交通说明的每个事项补上「从上一地点怎么过来 + 大约耗时」"),
            new RuleSpec("R5", "每日交通小结", "阻断", "MODEL",
                    "每天给出一段当天景点之间的交通说明",
                    null,
                    "为每天补写当日景点之间的交通小结（方式 + 耗时）"),
            new RuleSpec("R6", "往返交通", "阻断", "MODEL",
                    "去程与返程都要写明具体交通方式与耗时",
                    null,
                    "补齐去程与返程的具体交通方式（高铁/动车/航班/自驾等）与大致耗时"),
            new RuleSpec("R7", "市内交通", "阻断", "MODEL",
                    "给出市内交通总体建议，并列出至少 2 种可选方式",
                    null,
                    "补写市内交通总体建议，并列出至少 2 种可选方式与票价参考"),
            new RuleSpec("R8", "住宿推荐与预算匹配", "阻断", "AUTO+MODEL",
                    "至少 3 家酒店且含 1 家首选推荐，字段完整，并至少 1 家单价不高于每晚预算",
                    "补齐酒店分类（推荐/备选）与档次标签",
                    "补充酒店至至少 3 家并标注 1 家为首选推荐，且至少 1 家每晚单价低于当前住宿预算"),
            new RuleSpec("R9", "天数一致", "阻断", "AUTO",
                    "实际生成天数等于用户要求天数",
                    "超出则裁剪、不足则补足，并重排第几天序号", null),
            new RuleSpec("R10", "预算完整性", "警告", "RECORD",
                    "给出分项预算与预算合计",
                    null, null),
            new RuleSpec("R11", "购物推荐", "阻断", "MODEL",
                    "推荐 2-5 个购物地点或区域，写明适合买什么，不设任何额度",
                    null,
                    "补充至 2-5 个具体购物地点或商圈，并写明各自适合买什么（不设购物额度）")
    );

    /** 全部规则定义（供前端展示、测试枚举与文档核对） */
    public List<RuleSpec> rules() {
        return RULES;
    }

    /** 按编号取规则定义，未收录返回 null */
    public RuleSpec rule(String id) {
        return RULES.stream().filter(r -> r.getRule().equals(id)).findFirst().orElse(null);
    }

    /**
     * 当前质检结果中、可交给机器自动修复的阻断规则（去重、有序）。
     * 警告项一律不返回 —— 这是「警告永不触发升级处理」的代码落点。
     */
    public List<String> autoFixableRules(Itinerary.Quality q) {
        return blockingRules(q, true);
    }

    /**
     * 当前质检结果中、必须靠定向重写才能解决的阻断规则（去重、有序）。
     */
    public List<String> rewriteRules(Itinerary.Quality q) {
        return blockingRules(q, false);
    }

    private List<String> blockingRules(Itinerary.Quality q, boolean autoFix) {
        if (q == null || q.getIssues() == null) {
            return List.of();
        }
        return q.getIssues().stream()
                .filter(i -> "阻断".equals(i.getLevel()))
                .map(Itinerary.Issue::getRule)
                .distinct()
                .filter(id -> {
                    RuleSpec spec = rule(id);
                    return spec != null && (autoFix ? spec.isAutoFixable() : spec.needsRewrite());
                })
                .toList();
    }

    /* ================================ 判定依据 ================================ */

    /**
     * 逐条校验行程是否满足标准。
     *
     * @param it  待校验行程
     * @param req 用户的原始出行条件（用于校验天数一致性）
     */
    public Itinerary.Quality check(Itinerary it, PlanGenerateRequest req) {
        List<Itinerary.Issue> issues = new ArrayList<>();
        Itinerary.Quality q = new Itinerary.Quality();

        if (it == null) {
            issues.add(issue("R0", "方案完整性", "阻断", "整份方案", "未生成任何行程内容，请重新生成"));
            return finish(q, issues);
        }

        // ---------- R1 地点具体性 ----------
        List<Itinerary.DayPlan> days = it.getDays() == null ? List.of() : it.getDays();
        for (int di = 0; di < days.size(); di++) {
            Itinerary.DayPlan day = days.get(di);
            List<Itinerary.Item> items = day.getItems() == null ? List.of() : day.getItems();
            for (int ii = 0; ii < items.size(); ii++) {
                String place = items.get(ii).getPlace();
                if (!StringUtils.hasText(place)) {
                    issues.add(issue("R1", "地点具体性", "阻断", pos(di, ii),
                            "缺少地点名称，必须写出真实具体的地名"));
                } else if (!isPlaceConcrete(place)) {
                    issues.add(issue("R1", "地点具体性", "阻断", pos(di, ii),
                            "地点“" + place.trim() + "”属于泛称，需替换为真实地点全称（如具体景区、街区、博物馆或店名）"));
                }
            }

            // ---------- R2 每日事项数量 ----------
            if (items.size() < MIN_ITEMS_PER_DAY) {
                issues.add(issue("R2", "每日事项数量", "警告", "第 " + (di + 1) + " 天",
                        "当天仅 " + items.size() + " 个事项，标准要求每天至少 " + MIN_ITEMS_PER_DAY + " 个"));
            }

            // ---------- R3 游览顺序（时间递增） ----------
            int prev = -1;
            for (int ii = 0; ii < items.size(); ii++) {
                int start = parseStartMinutes(items.get(ii).getTime());
                if (start < 0) {
                    continue;
                }
                if (prev >= 0 && start < prev) {
                    issues.add(issue("R3", "游览顺序", "阻断", pos(di, ii),
                            "时间“" + items.get(ii).getTime() + "”早于上一事项，需按时间先后重排游览顺序"));
                }
                prev = Math.max(prev, start);
            }

            // ---------- R4 每日交通小结 ----------
            if (!StringUtils.hasText(day.getTransport())) {
                issues.add(issue("R5", "每日交通小结", "阻断", "第 " + (di + 1) + " 天",
                        "缺少当天景点之间的交通方式说明"));
            }
        }

        // ---------- R6 点对点交通 ----------
        for (int di = 0; di < days.size(); di++) {
            List<Itinerary.Item> items = days.get(di).getItems() == null ? List.of() : days.get(di).getItems();
            for (int ii = 0; ii < items.size(); ii++) {
                if (!StringUtils.hasText(items.get(ii).getTransport())) {
                    issues.add(issue("R4", "点对点交通", "阻断", pos(di, ii),
                            "缺少从上一地点到达此处的交通方式与耗时"));
                }
            }
        }

        // ---------- R7 天数一致 ----------
        int expectDays = req == null || req.getDays() == null ? days.size() : req.getDays();
        if (expectDays > 0 && days.size() != expectDays) {
            issues.add(issue("R9", "天数一致", "阻断", "整份方案",
                    "用户要求 " + expectDays + " 天，实际生成 " + days.size() + " 天"));
        }

        // ---------- R8 城际往返 + 市内交通 ----------
        Itinerary.Transport t = it.getTransport();
        if (t == null) {
            issues.add(issue("R6", "往返交通", "阻断", "整份方案", "缺少往返交通安排"));
        } else {
            if (!StringUtils.hasText(t.getOutbound())) {
                issues.add(issue("R6", "往返交通", "阻断", "去程", "缺少出发地到目的地的去程交通"));
            } else if (!containsAny(t.getOutbound(), TRANSPORT_KEYWORDS)) {
                issues.add(issue("R6", "往返交通", "警告", "去程",
                        "去程未写明具体交通方式（建议至少包含高铁/动车/航班/机场等）"));
            }
            if (!StringUtils.hasText(t.getInbound())) {
                issues.add(issue("R6", "往返交通", "阻断", "返程", "缺少目的地返回出发地的返程交通"));
            } else if (!containsAny(t.getInbound(), TRANSPORT_KEYWORDS)) {
                issues.add(issue("R6", "往返交通", "警告", "返程",
                        "返程未写明具体交通方式（建议至少包含高铁/动车/航班/机场等）"));
            }
            if (!StringUtils.hasText(t.getLocal())) {
                issues.add(issue("R7", "市内交通", "阻断", "市内交通", "缺少目的地市内交通总体建议"));
            }
            int optionCount = t.getOptions() == null ? 0 : (int) t.getOptions().stream().filter(StringUtils::hasText).count();
            if (optionCount < MIN_TRANSPORT_OPTIONS) {
                issues.add(issue("R7", "市内交通", "警告", "可选方式",
                        "可选交通方式仅 " + optionCount + " 条，标准要求至少 " + MIN_TRANSPORT_OPTIONS + " 条"));
            }
        }

        // ---------- R8 住宿推荐 ----------
        List<Itinerary.Hotel> hotels = it.getHotels() == null ? List.of() : it.getHotels();
        if (hotels.size() < MIN_HOTELS) {
            issues.add(issue("R8", "住宿推荐", "阻断", "住宿",
                    "仅给出 " + hotels.size() + " 家酒店，标准要求至少 " + MIN_HOTELS + " 家（1 家推荐 + 若干备选）"));
        }
        long recommendCount = hotels.stream()
                .filter(h -> StringUtils.hasText(h.getCategory()) && h.getCategory().contains("推荐"))
                .count();
        if (!hotels.isEmpty() && recommendCount == 0) {
            issues.add(issue("R8", "住宿推荐", "阻断", "住宿", "未指定首选推荐酒店（category 需含“推荐”）"));
        }
        for (int hi = 0; hi < hotels.size(); hi++) {
            Itinerary.Hotel h = hotels.get(hi);
            String label = "第 " + (hi + 1) + " 家酒店";
            checkHotelField(issues, h.getName(), "名称", label);
            checkHotelField(issues, h.getArea(), "所在区域", label);
            checkHotelField(issues, h.getPrice(), "大致价位", label);
            checkHotelField(issues, h.getReason(), "推荐理由", label);
            checkHotelField(issues, h.getAdvantage(), "位置优势", label);
            if (!StringUtils.hasText(h.getLevel())) {
                issues.add(issue("R8", "住宿推荐", "警告", label, "缺少档次标签（经济型/舒适型/高档型）"));
            }
        }

        // ---------- R8 住宿与预算匹配（预算变化时必须给得出匹配的酒店） ----------
        Itinerary.StayPlan sp = it.getStay();
        if (sp != null && sp.getBudgetPerNight() != null && sp.getBudgetPerNight() > 0 && !hotels.isEmpty()) {
            int per = sp.getBudgetPerNight();
            boolean anyWithin = hotels.stream().anyMatch(h -> {
                Integer p = priceOf(h);
                return p != null && p > 0 && p <= per;
            });
            if (!anyWithin) {
                issues.add(issue("R8", "住宿预算匹配", "阻断", "住宿",
                        "没有任何酒店落在每晚预算 ¥" + per + " 之内，请上调住宿预算或补充更经济的酒店，"
                                + "保证预算区间内始终有可选住宿"));
            }
        }

        // ---------- R11 购物推荐（只推荐地点/区域，不设额度） ----------
        List<Itinerary.ShoppingSpot> shops = it.getShopping() == null ? List.of() : it.getShopping();
        if (shops.size() < MIN_SHOPPING_SPOTS) {
            issues.add(issue("R11", "购物推荐", "阻断", "购物推荐",
                    "仅给出 " + shops.size() + " 个购物地点/区域，标准要求至少 " + MIN_SHOPPING_SPOTS
                            + " 个（只推荐去处，不设额度）"));
        }
        for (int si = 0; si < shops.size(); si++) {
            Itinerary.ShoppingSpot s = shops.get(si);
            String label = "第 " + (si + 1) + " 个购物点";
            if (!StringUtils.hasText(s.getName())) {
                issues.add(issue("R11", "购物推荐", "阻断", label, "缺少地点或区域名称"));
            }
            if (!StringUtils.hasText(s.getBuyWhat())) {
                issues.add(issue("R11", "购物推荐", "警告", label, "缺少「适合买什么」"));
            }
            if (!StringUtils.hasText(s.getType()) && !StringUtils.hasText(s.getArea())) {
                issues.add(issue("R11", "购物推荐", "警告", label, "缺少类型与所在区域"));
            }
        }

        // ---------- R10 预算完整性 ----------
        Itinerary.BudgetBreakdown b = it.getBudget();
        if (b == null) {
            issues.add(issue("R10", "预算完整性", "警告", "预算", "缺少预算分配"));
        } else if (!StringUtils.hasText(b.getTotal())) {
            issues.add(issue("R10", "预算完整性", "警告", "预算", "缺少预算合计"));
        }

        return finish(q, issues);
    }

    private void checkHotelField(List<Itinerary.Issue> issues, String value, String field, String label) {
        if (!StringUtils.hasText(value)) {
            issues.add(issue("R8", "住宿推荐", "阻断", label, "缺少「" + field + "」"));
        }
    }

    /* ============================ 不符合标准时的处理 ============================ */

    /**
     * 可自动修复项兜底：时间乱序、天数不一致、酒店档次/分类缺失默认值。
     *
     * @return 是否发生过修改
     */
    public boolean repair(Itinerary it, PlanGenerateRequest req) {
        if (it == null || it.getDays() == null) {
            return false;
        }
        boolean changed = false;

        // R3：按起始时间重新排序
        for (Itinerary.DayPlan day : it.getDays()) {
            List<Itinerary.Item> items = day.getItems();
            if (items == null || items.size() < 2) {
                continue;
            }
            List<Itinerary.Item> sorted = new ArrayList<>(items);
            sorted.sort(Comparator.comparingInt(i -> {
                int v = parseStartMinutes(i.getTime());
                return v < 0 ? Integer.MAX_VALUE : v;
            }));
            if (!sorted.equals(items)) {
                day.setItems(sorted);
                changed = true;
            }
        }

        // R9：天数对齐
        int expect = req == null || req.getDays() == null ? it.getDays().size() : req.getDays();
        if (expect > 0) {
            List<Itinerary.DayPlan> list = new ArrayList<>(it.getDays());
            while (list.size() > expect) {
                list.remove(list.size() - 1);
                changed = true;
            }
            while (list.size() < expect) {
                Itinerary.DayPlan last = list.isEmpty() ? new Itinerary.DayPlan() : list.get(list.size() - 1);
                Itinerary.DayPlan clone = new Itinerary.DayPlan();
                clone.setDay(list.size() + 1);
                clone.setDate("第 " + (list.size() + 1) + " 天");
                clone.setTheme("自由活动 / 机动日");
                clone.setTransport(last.getTransport());
                List<Itinerary.Item> copied = new ArrayList<>();
                if (last.getItems() != null) {
                    for (Itinerary.Item src : last.getItems()) {
                        Itinerary.Item one = new Itinerary.Item();
                        one.setTime(src.getTime());
                        one.setPlace(src.getPlace());
                        one.setActivity(src.getActivity());
                        one.setTransport(src.getTransport());
                        one.setCost(src.getCost());
                        one.setTips(src.getTips());
                        copied.add(one);
                    }
                }
                clone.setItems(copied);
                list.add(clone);
                changed = true;
            }
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getDay() == null || list.get(i).getDay() != i + 1) {
                    list.get(i).setDay(i + 1);
                    changed = true;
                }
            }
            it.setDays(list);
        }

        // R8：补齐酒店分类与档次默认值
        if (it.getHotels() != null) {
            for (int i = 0; i < it.getHotels().size(); i++) {
                Itinerary.Hotel h = it.getHotels().get(i);
                if (!StringUtils.hasText(h.getCategory())) {
                    h.setCategory(i == 0 ? "推荐" : "备选");
                    changed = true;
                }
                if (!StringUtils.hasText(h.getLevel())) {
                    h.setLevel("舒适型");
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * 无法自动修复时，生成给大模型的**定向**纠正指令，触发一次重写。
     *
     * <p>与「把所有问题一股脑丢回去」不同，这里按规则分组输出三样东西：
     * 该规则的判定要求、该规则的修正动作、以及具体出错的位置。
     * 只输出<b>阻断项</b>——警告项不参与重写，避免为不阻断的问题反复消耗调用。</p>
     */
    public String buildCorrection(Itinerary it, PlanGenerateRequest req, Itinerary.Quality quality) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【上一次输出未通过行程质量标准，请在保持 JSON 结构完全不变的前提下定向修正】\n");
        if (quality == null || quality.getIssues() == null) {
            sb.append("请重新输出完整且唯一的 JSON。\n");
            return sb.toString();
        }
        Map<String, List<Itinerary.Issue>> byRule = new LinkedHashMap<>();
        for (Itinerary.Issue i : quality.getIssues()) {
            if ("阻断".equals(i.getLevel())) {
                byRule.computeIfAbsent(i.getRule(), k -> new ArrayList<>()).add(i);
            }
        }
        for (Map.Entry<String, List<Itinerary.Issue>> e : byRule.entrySet()) {
            RuleSpec spec = rule(e.getKey());
            sb.append("\n[").append(e.getKey()).append(' ')
                    .append(e.getValue().get(0).getName()).append("] 必须满足：")
                    .append(spec == null ? e.getValue().get(0).getName() : spec.getRequirement());
            if (spec != null && StringUtils.hasText(spec.getCorrection())) {
                sb.append("；修正方式：").append(spec.getCorrection());
            }
            sb.append('\n');
            for (Itinerary.Issue i : e.getValue()) {
                sb.append("  - ").append(i.getTarget()).append("：").append(i.getDetail()).append('\n');
            }
        }
        sb.append("\n以上条目逐条修正后，重新输出完整且唯一的 JSON。\n");
        return sb.toString();
    }

    /* ====================== 住宿预算与酒店选择（前端实时核算的数据基础） ====================== */

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    /**
     * 解析「350-500 元/晚」「约 400 元/晚」这类价位文本，取区间中值作为每晚参考价。
     * 前端切换酒店时需要用它实时重算住宿花费，因此必须落到数字上。
     *
     * @return 每晚参考价（元），无法解析时返回 null
     */
    public Integer parsePricePerNight(String price) {
        if (!StringUtils.hasText(price)) {
            return null;
        }
        Matcher m = NUMBER.matcher(price.replace(",", ""));
        List<Double> nums = new ArrayList<>();
        while (m.find() && nums.size() < 4) {
            try {
                nums.add(Double.parseDouble(m.group()));
            } catch (NumberFormatException ignored) {
                // 忽略异常数字片段
            }
        }
        if (nums.isEmpty()) {
            return null;
        }
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (double n : nums) {
            lo = Math.min(lo, n);
            hi = Math.max(hi, n);
        }
        int v = (int) Math.round(nums.size() == 1 ? nums.get(0) : (lo + hi) / 2.0);
        return v > 0 ? v : null;
    }

    /**
     * 取文本里出现的第一个数字，用于解析「约 2400 元（含住宿与餐饮）」这类金额。
     * 与 {@link #parsePricePerNight} 的区别：后者会把区间取中值，不适合含多个数字的长句。
     */
    public Integer parseFirstAmount(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher m = NUMBER.matcher(text.replace(",", ""));
        if (m.find()) {
            try {
                double v = Double.parseDouble(m.group());
                return v > 0 ? (int) Math.round(v) : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 初始化住宿决策：解析每家酒店的每晚参考价，并补齐住宿预算的默认值。
     *
     * <p>语义是「只补缺、不覆盖」——用户已经调整过的每晚预算与选定的酒店会原样保留，
     * 因此同一方法可以安全地在「生成」与「编辑保存」两条链路上重复调用。</p>
     *
     * @return 是否发生过修改
     */
    public boolean initStayPlan(Itinerary it, PlanGenerateRequest req) {
        int days = req == null || req.getDays() == null ? 0 : req.getDays();
        return initStayPlan(it, days);
    }

    public boolean initStayPlan(Itinerary it, int days) {
        if (it == null || it.getHotels() == null || it.getHotels().isEmpty()) {
            return false;
        }
        boolean changed = false;
        List<Itinerary.Hotel> hotels = it.getHotels();

        // 1) 解析每晚参考价（酒店价格改过时会重新解析）
        for (Itinerary.Hotel h : hotels) {
            if (h.getPricePerNight() == null || h.getPricePerNight() <= 0) {
                Integer p = parsePricePerNight(h.getPrice());
                if (p != null) {
                    h.setPricePerNight(p);
                    changed = true;
                }
            }
        }

        // 2) 住宿决策默认值：晚数 = 天数 - 1；默认选推荐酒店；默认预算 = 该酒店单价
        Itinerary.StayPlan stay = it.getStay();
        if (stay == null) {
            stay = new Itinerary.StayPlan();
            it.setStay(stay);
            changed = true;
        }
        int expectDays = days > 0 ? days : (it.getDays() == null ? 1 : it.getDays().size());
        int nights = Math.max(1, expectDays - 1);
        if (stay.getNights() == null || stay.getNights() <= 0) {
            stay.setNights(nights);
            changed = true;
        }

        // 每晚预算：优先按整体预算里的住宿金额反推（保证默认与总预算自洽），否则用推荐酒店单价
        if (stay.getBudgetPerNight() == null || stay.getBudgetPerNight() <= 0) {
            Integer fromBudget = null;
            if (it.getBudget() != null) {
                Integer acc = parseFirstAmount(it.getBudget().getAccommodation());
                if (acc != null && acc > 0) {
                    fromBudget = Math.max(1, (int) Math.round(acc / (double) nights));
                }
            }
            if (fromBudget == null) {
                fromBudget = priceOf(pickRecommend(hotels));
            }
            if (fromBudget != null && fromBudget > 0) {
                stay.setBudgetPerNight(fromBudget);
                changed = true;
            }
        }

        // 默认选中：在每晚预算之内的酒店里挑单价最高的一家（预算内最优）；全部超出则挑最便宜的
        String wantName = stay.getSelectedHotel();
        boolean exists = StringUtils.hasText(wantName)
                && hotels.stream().anyMatch(h -> wantName.equals(h.getName()));
        if (!exists) {
            stay.setSelectedHotel(pickWithinBudget(hotels, stay.getBudgetPerNight()).getName());
            changed = true;
        }
        return changed;
    }

    /** 取推荐酒店（category 含“推荐”），没有则第一家 */
    private Itinerary.Hotel pickRecommend(List<Itinerary.Hotel> hotels) {
        return hotels.stream()
                .filter(h -> StringUtils.hasText(h.getCategory()) && h.getCategory().contains("推荐"))
                .findFirst().orElse(hotels.get(0));
    }

    /** 每晚单价：优先用已解析字段，其次现场解析并回填 */
    private Integer priceOf(Itinerary.Hotel h) {
        if (h == null) {
            return null;
        }
        if (h.getPricePerNight() != null && h.getPricePerNight() > 0) {
            return h.getPricePerNight();
        }
        Integer p = parsePricePerNight(h.getPrice());
        if (p != null) {
            h.setPricePerNight(p);
        }
        return p;
    }

    /** 在预算内挑单价最高的酒店；若全部超预算则挑最便宜的 */
    private Itinerary.Hotel pickWithinBudget(List<Itinerary.Hotel> hotels, Integer perNight) {
        int limit = perNight == null || perNight <= 0 ? Integer.MAX_VALUE : perNight;
        Itinerary.Hotel best = null;
        Itinerary.Hotel cheapest = null;
        for (Itinerary.Hotel h : hotels) {
            Integer p = priceOf(h);
            if (p == null || p <= 0) {
                continue;
            }
            if (cheapest == null || p < priceOf(cheapest)) {
                cheapest = h;
            }
            if (p <= limit && (best == null || p > priceOf(best))) {
                best = h;
            }
        }
        if (best != null) {
            return best;
        }
        return cheapest != null ? cheapest : hotels.get(0);
    }

    /* ================================ 工具方法 ================================ */

    private Itinerary.Quality finish(Itinerary.Quality q, List<Itinerary.Issue> issues) {
        List<Itinerary.Issue> blocking = issues.stream().filter(i -> "阻断".equals(i.getLevel())).toList();
        q.setIssues(issues);
        q.setIssueCount(blocking.size());
        q.setPassed(blocking.isEmpty());
        int total = TOTAL_RULES;
        int failed = (int) issues.stream().map(Itinerary.Issue::getRule).distinct().count();
        q.setScore(Math.max(0, Math.round((total - failed) * 100f / total)));
        return q;
    }

    /** 校验地点是否为具体名称 */
    public boolean isPlaceConcrete(String place) {
        if (!StringUtils.hasText(place)) {
            return false;
        }
        String p = place.trim();
        if (p.length() < MIN_PLACE_LENGTH) {
            return false;
        }
        for (String banned : BANNED_PLACE_WORDS) {
            if (p.contains(banned)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsAny(String text, String[] keywords) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    /** 解析「09:00-11:30」或「09:00」的起始分钟数，解析失败返回 -1 */
    public int parseStartMinutes(String time) {
        if (!StringUtils.hasText(time)) {
            return -1;
        }
        String s = time.trim();
        int dash = s.indexOf('-');
        if (dash < 0) {
            dash = s.indexOf('—');
        }
        if (dash > 0) {
            s = s.substring(0, dash);
        }
        String[] parts = s.trim().split(":");
        if (parts.length < 2) {
            return -1;
        }
        try {
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].replaceAll("\\D", ""));
            if (h < 0 || h > 23 || m < 0 || m > 59) {
                return -1;
            }
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    private Itinerary.Issue issue(String rule, String name, String level, String target, String detail) {
        Itinerary.Issue i = new Itinerary.Issue();
        i.setRule(rule);
        i.setName(name);
        i.setLevel(level);
        i.setTarget(target);
        i.setDetail(detail);
        return i;
    }

    private String pos(int dayIndex, int itemIndex) {
        return "第 " + (dayIndex + 1) + " 天 · 第 " + (itemIndex + 1) + " 项";
    }
}
