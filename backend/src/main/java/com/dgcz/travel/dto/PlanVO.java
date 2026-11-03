package com.dgcz.travel.dto;

import com.dgcz.travel.entity.TravelPlan;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 方案列表/详情视图对象
 */
@Data
public class PlanVO {

    private Long id;
    private Long userId;
    private String title;
    private String origin;
    private String destination;
    private Integer days;
    private java.math.BigDecimal budget;
    private String hobbies;
    private String preferences;
    private String summary;
    private Itinerary itinerary;
    private String model;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static PlanVO from(TravelPlan plan, Itinerary itinerary) {
        PlanVO vo = new PlanVO();
        vo.setId(plan.getId());
        vo.setUserId(plan.getUserId());
        vo.setTitle(plan.getTitle());
        vo.setOrigin(plan.getOrigin());
        vo.setDestination(plan.getDestination());
        vo.setDays(plan.getDays());
        vo.setBudget(plan.getBudget());
        vo.setHobbies(plan.getHobbies());
        vo.setPreferences(plan.getPreferences());
        vo.setSummary(plan.getSummary());
        vo.setItinerary(itinerary);
        vo.setModel(plan.getModel());
        vo.setCreateTime(plan.getCreateTime());
        vo.setUpdateTime(plan.getUpdateTime());
        return vo;
    }
}
