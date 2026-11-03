package com.dgcz.travel.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dgcz.travel.common.Result;
import com.dgcz.travel.common.UserContext;
import com.dgcz.travel.dto.AdminStatsVO;
import com.dgcz.travel.dto.AdminUserVO;
import com.dgcz.travel.dto.HotelRequest;
import com.dgcz.travel.dto.PlanVO;
import com.dgcz.travel.entity.Hotel;
import com.dgcz.travel.service.AdminService;
import com.dgcz.travel.service.HotelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台接口：数据概览 / 用户数据 / 酒店维护
 *
 * <p>整个 /api/admin/** 由 AdminInterceptor 统一鉴权，只有 role=ADMIN 且账号未禁用的用户可访问。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final HotelService hotelService;

    /* ------------------------------- 数据概览 ------------------------------- */

    @GetMapping("/stats")
    public Result<AdminStatsVO> stats() {
        return Result.ok(adminService.overview());
    }

    /* ------------------------------- 用户数据 ------------------------------- */

    /** 用户分页列表：支持按用户名/昵称搜索、按角色与状态筛选 */
    @GetMapping("/users")
    public Result<IPage<AdminUserVO>> users(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String role,
                                            @RequestParam(required = false) Integer status) {
        return Result.ok(adminService.pageUsers(pageNum, pageSize, keyword, role, status));
    }

    /** 禁用 / 启用用户 */
    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.updateStatus(UserContext.require(), id, status);
        return Result.ok();
    }

    /** 查看某个用户生成的方案（只读） */
    @GetMapping("/users/{id}/plans")
    public Result<IPage<PlanVO>> userPlans(@PathVariable Long id,
                                           @RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String keyword) {
        return Result.ok(adminService.userPlans(id, pageNum, pageSize, keyword));
    }

    /* ------------------------------- 酒店管理 ------------------------------- */

    @GetMapping("/hotels")
    public Result<IPage<Hotel>> hotels(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String city,
                                       @RequestParam(required = false) Integer status) {
        return Result.ok(hotelService.page(pageNum, pageSize, keyword, city, status));
    }

    @PostMapping("/hotels")
    public Result<Hotel> createHotel(@RequestBody @Valid HotelRequest req) {
        return Result.ok(hotelService.create(req));
    }

    @PutMapping("/hotels/{id}")
    public Result<Hotel> updateHotel(@PathVariable Long id, @RequestBody @Valid HotelRequest req) {
        return Result.ok(hotelService.update(id, req));
    }

    /** 上架 / 下架 */
    @PutMapping("/hotels/{id}/status")
    public Result<Hotel> updateHotelStatus(@PathVariable Long id, @RequestParam Integer status) {
        return Result.ok(hotelService.updateStatus(id, status));
    }

    @DeleteMapping("/hotels/{id}")
    public Result<Void> removeHotel(@PathVariable Long id) {
        hotelService.remove(id);
        return Result.ok();
    }

    /** 已有城市列表，供筛选下拉使用 */
    @GetMapping("/hotels/cities")
    public Result<List<String>> hotelCities() {
        return Result.ok(hotelService.cities());
    }
}
