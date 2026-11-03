package com.dgcz.travel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 单条行程质量规则的元数据：把「判定标准」与「不通过时走哪一级处理」绑在一起，
 * 使三级链路（自动校验 → 定向重写 → 降级兜底）的触发条件可枚举、可测试、可追溯。
 */
@Data
@AllArgsConstructor
public class RuleSpec {

    /** 规则编号，如 R1 */
    private String rule;

    /** 规则名称 */
    private String name;

    /** 级别：阻断 / 警告。警告不阻断通过，也永不触发升级处理 */
    private String level;

    /** 处理方式：AUTO 可机器自动修复 / MODEL 需定向重写 / AUTO+MODEL 先修后写 / RECORD 仅记录 */
    private String handler;

    /** 判定要求（一句话） */
    private String requirement;

    /** 该规则可自动修复时的修复动作，不可修复时为 null */
    private String autoFix;

    /** 未通过时给大模型的定向纠正要求，仅 MODEL 类规则有值 */
    private String correction;

    public boolean isBlocking() {
        return "阻断".equals(level);
    }

    public boolean isAutoFixable() {
        return handler != null && handler.contains("AUTO");
    }

    public boolean needsRewrite() {
        return handler != null && handler.contains("MODEL");
    }
}
