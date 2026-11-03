package com.dgcz.travel.dto;

import lombok.Data;

import java.util.List;

/**
 * 大模型返回的行程结构（与前端展示/编辑一致）
 */
@Data
public class Itinerary {

    /** 方案标题 */
    private String title;

    /** 行程总览 */
    private String summary;

    /** 每日安排 */
    private List<DayPlan> days;

    /** 城际往返 + 市内交通 */
    private Transport transport;

    /** 住宿推荐（第一条为推荐，其余为备选） */
    private List<Hotel> hotels;

    /**
     * 住宿决策：用户可调整每晚住宿预算、切换选定酒店。
     * 系统按当前每晚预算实时匹配推荐酒店（预算内单价最高者）；未用完的住宿预算
     * 只作为结余提示，不预划任何购物额度。
     */
    private StayPlan stay;

    /**
     * 购物地点 / 区域推荐。
     * 仅作参考信息：不设额度、不计入预算，是否购物与消费多少完全由用户自行决定。
     */
    private List<ShoppingSpot> shopping;

    /** 预算分配 */
    private BudgetBreakdown budget;

    /** 出行提示 */
    private List<String> tips;

    /** 行程质量标准校验结果（由后端统一填充，供前端展示合规情况） */
    private Quality quality;

    /** 质检结果 */
    @Data
    public static class Quality {
        /** 是否全部达标 */
        private Boolean passed;
        /** 达标率 0-100 */
        private Integer score;
        /** 未达标条数（仅统计阻断项） */
        private Integer issueCount;
        /** 数据来源：大模型 / 大模型（定向重写后） / 兜底示例（原因） / 内置示例 */
        private String source;
        /** 未达标明细 */
        private List<Issue> issues;
        /**
         * 三级处理链路的执行轨迹（L1 自动校验 / L2 定向重写 / L3 降级兜底）。
         * 每个阶段都会留痕：是否触发、依据哪些规则触发、处理前后阻断项数、产出是否被采纳。
         */
        private List<Stage> stages;
    }

    /** 三级链路中的单个阶段记录 */
    @Data
    public static class Stage {
        /** 阶段编号：L1 / L2 / L3 */
        private String stage;
        /** 阶段动作：自动校验 / 规则自动修复 / 定向重写 / 降级兜底 */
        private String action;
        /** 触发依据：触发该阶段的规则集合与原因；未触发时为「未触发」及原因 */
        private String trigger;
        /** 处理前阻断项数 */
        private Integer issuesBefore;
        /** 处理后阻断项数 */
        private Integer issuesAfter;
        /** 该阶段产出是否被采纳 */
        private Boolean adopted;
        /** 补充说明：跳过、失败、未采纳的原因 */
        private String note;
    }

    /** 单条不达标明细 */
    @Data
    public static class Issue {
        /** 规则编号，如 R1 */
        private String rule;
        /** 规则名称 */
        private String name;
        /** 级别：阻断 / 警告 */
        private String level;
        /** 定位：如「第 2 天 · 第 3 项」 */
        private String target;
        /** 说明与处理方式 */
        private String detail;
    }

    @Data
    public static class DayPlan {
        private Integer day;
        private String theme;
        private String date;
        /** 当天交通小结：景点之间怎么走 */
        private String transport;
        private List<Item> items;
    }

    @Data
    public static class Item {
        /** 时间段，如 09:00-11:00 */
        private String time;
        /** 地点名称 */
        private String place;
        /** 具体安排 */
        private String activity;
        /** 从上一地点到这里的交通方式与耗时 */
        private String transport;
        /** 预估花费 */
        private String cost;
        /** 小贴士 */
        private String tips;
    }

    /** 城际往返 + 市内交通 */
    @Data
    public static class Transport {
        /** 去程：出发地 → 目的地 */
        private String outbound;
        /** 返程：目的地 → 出发地 */
        private String inbound;
        /** 市内交通总体建议 */
        private String local;
        /** 可选交通方式与票价参考 */
        private List<String> options;
    }

    /** 住宿推荐 */
    @Data
    public static class Hotel {
        /** 酒店名称 */
        private String name;
        /** 所在区域 */
        private String area;
        /** 大致价位，如 350-500 元/晚 */
        private String price;
        /** 每晚参考价（元/晚），由 price 文本解析而来，供住宿预算实时核算 */
        private Integer pricePerNight;
        /** 推荐理由 */
        private String reason;
        /** 位置优势 */
        private String advantage;
        /** 档次标签：经济型 / 舒适型 / 高档型 */
        private String level;
        /** 推荐 / 备选 */
        private String category;
    }

    /**
     * 购物地点 / 区域推荐（纯参考，不含任何额度）。
     * 系统只告诉用户「去哪买、买什么」，是否消费由用户自行决定。
     */
    @Data
    public static class ShoppingSpot {
        /** 地点或区域名称，如「坡子街」「黄兴路步行街 & IFS 国金中心」 */
        private String name;
        /** 所在区域 / 商圈 */
        private String area;
        /** 类型：特产伴手礼 / 文创 / 服饰美妆 / 综合商圈 / 农贸批发 等 */
        private String type;
        /** 适合买什么（具体品类或商品） */
        private String buyWhat;
        /** 适合人群 / 场景 */
        private String suitableFor;
        /** 实用提示：营业时间、砍价、快递等 */
        private String tips;
    }

    /** 住宿决策（用户可调） */
    @Data
    public static class StayPlan {
        /** 每晚住宿预算（元/晚），用户可调整；系统据此实时匹配推荐酒店 */
        private Integer budgetPerNight;
        /** 入住晚数，默认 = 出行天数 - 1 */
        private Integer nights;
        /** 选中的酒店名称（对应 hotels[].name） */
        private String selectedHotel;
        /**
         * 用户在地图上自行指定的住宿位置（可为空）。
         * 为空时按 selectedHotel 的名称解析坐标；不为空时优先使用这里的坐标作为
         * 每天动线的出发与返回点 —— 因此改变该位置会重排所有天的路线。
         * 该字段必须在本类中声明，否则保存时会被反序列化丢弃。
         */
        private MapLocation customLocation;
        /**
         * 自选住宿的每晚花销（元/晚）。
         * 用户不使用推荐酒店时，推荐列表里的单价与真实开销无关，由用户自行填写，
         * 用于把住宿费计入总花销；为空或 0 时回退到 selectedHotel 的单价。
         */
        private Integer customPricePerNight;
    }

    /** 地图上指定的一个位置 */
    @Data
    public static class MapLocation {
        /** 名称（搜索结果的标题，或地图点选时的推荐地址） */
        private String name;
        /** 详细地址 */
        private String address;
        private Double lat;
        private Double lng;
        /** 来源：search-搜索结果，map-地图点选 */
        private String source;
    }

    @Data
    public static class BudgetBreakdown {
        private String transport;
        private String accommodation;
        private String food;
        private String tickets;
        private String other;
        private String total;
    }
}
