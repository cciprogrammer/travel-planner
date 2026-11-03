package com.dgcz.travel.controller;

import com.dgcz.travel.common.Result;
import com.dgcz.travel.common.UserContext;
import com.dgcz.travel.dto.FavoriteRequest;
import com.dgcz.travel.entity.FavoritePlace;
import com.dgcz.travel.service.FavoriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 地点收藏接口
 */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public Result<List<FavoritePlace>> list() {
        return Result.ok(favoriteService.list(UserContext.require()));
    }

    @PostMapping
    public Result<Map<String, Object>> add(@RequestBody @Valid FavoriteRequest req) {
        Long id = favoriteService.add(UserContext.require(), req);
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        return Result.ok(data);
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        favoriteService.remove(UserContext.require(), id);
        return Result.ok();
    }
}
