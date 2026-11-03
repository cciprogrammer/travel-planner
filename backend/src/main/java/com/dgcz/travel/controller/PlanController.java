package com.dgcz.travel.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dgcz.travel.common.Result;
import com.dgcz.travel.common.UserContext;
import com.dgcz.travel.dto.PlanGenerateRequest;
import com.dgcz.travel.dto.PlanUpdateRequest;
import com.dgcz.travel.dto.PlanVO;
import com.dgcz.travel.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 旅行方案接口：生成 / 列表 / 详情 / 编辑 / 删除 / 重新生成
 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    /** 1. 填写条件，调用大模型生成行程 */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody @Valid PlanGenerateRequest req) {
        Long userId = UserContext.require();
        Long id = planService.generate(userId, req);
        Map<String, Object> data = new HashMap<>();
        data.put("planId", id);
        return Result.ok(data);
    }

    /** 重新生成 */
    @PostMapping("/{id}/regenerate")
    public Result<Map<String, Object>> regenerate(@PathVariable Long id) {
        Long userId = UserContext.require();
        Long planId = planService.regenerate(userId, id);
        Map<String, Object> data = new HashMap<>();
        data.put("planId", planId);
        return Result.ok(data);
    }

    /** 历史方案列表（分页 + 关键字） */
    @GetMapping
    public Result<IPage<PlanVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                      @RequestParam(defaultValue = "10") int pageSize,
                                      @RequestParam(required = false) String keyword) {
        return Result.ok(planService.page(UserContext.require(), pageNum, pageSize, keyword));
    }

    /** 方案详情 */
    @GetMapping("/{id}")
    public Result<PlanVO> detail(@PathVariable Long id) {
        return Result.ok(planService.detail(UserContext.require(), id));
    }

    /** 修改方案 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid PlanUpdateRequest req) {
        planService.update(UserContext.require(), id, req);
        return Result.ok();
    }

    /** 删除方案 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        planService.remove(UserContext.require(), id);
        return Result.ok();
    }
}
