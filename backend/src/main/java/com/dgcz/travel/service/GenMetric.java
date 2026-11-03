package com.dgcz.travel.service;

/**
 * 单次生成的埋点载体：记录三级链路各阶段的阻断项数与耗时，供离线统计使用。
 * 字段顺序即 {@code GEN_METRIC} 一行的输出顺序。
 */
class GenMetric {

    Long planId;
    String destination;
    Integer days;
    boolean fromLlm;
    boolean degraded;
    int llmCalls;
    long llmMs;
    boolean firstPassed;
    int firstIssues;
    int firstScore;
    boolean repaired;
    int afterRepairIssues;
    boolean rewrote;
    int afterRewriteIssues = -1;
    boolean fellBack;
    int afterFallbackIssues = -1;
    boolean finalPassed;
    int finalIssues;
    int finalScore;
    String source;
    long totalMs;

    /** 本次生成走到了哪一级：L1 通过 / L1 修复 / L2 重写 / L3 兜底 / 未达标 */
    String stageReached = "L1";
}
