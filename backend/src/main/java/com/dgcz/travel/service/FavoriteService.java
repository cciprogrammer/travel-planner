package com.dgcz.travel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dgcz.travel.common.BusinessException;
import com.dgcz.travel.dto.FavoriteRequest;
import com.dgcz.travel.entity.FavoritePlace;
import com.dgcz.travel.mapper.FavoritePlaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 地点收藏服务
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoritePlaceMapper favoriteMapper;

    public List<FavoritePlace> list(Long userId) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<FavoritePlace>()
                .eq(FavoritePlace::getUserId, userId)
                .orderByDesc(FavoritePlace::getCreateTime));
    }

    public Long add(Long userId, FavoriteRequest req) {
        boolean exists = favoriteMapper.selectCount(new LambdaQueryWrapper<FavoritePlace>()
                .eq(FavoritePlace::getUserId, userId)
                .eq(FavoritePlace::getName, req.getName())) > 0;
        if (exists) {
            throw new BusinessException("该地点已在收藏中");
        }
        FavoritePlace place = new FavoritePlace();
        place.setUserId(userId);
        place.setPlanId(req.getPlanId());
        place.setName(req.getName());
        place.setCity(req.getCity());
        place.setCategory(req.getCategory());
        place.setAddress(req.getAddress());
        place.setDescription(req.getDescription());
        place.setRating(req.getRating());
        place.setImageUrl(req.getImageUrl());
        place.setCreateTime(LocalDateTime.now());
        favoriteMapper.insert(place);
        return place.getId();
    }

    public void remove(Long userId, Long id) {
        FavoritePlace place = favoriteMapper.selectById(id);
        if (place == null || !place.getUserId().equals(userId)) {
            throw new BusinessException(404, "收藏不存在或无权操作");
        }
        favoriteMapper.deleteById(id);
    }

    public long countByUser(Long userId) {
        Long c = favoriteMapper.selectCount(
                new LambdaQueryWrapper<FavoritePlace>().eq(FavoritePlace::getUserId, userId));
        return c == null ? 0 : c;
    }
}
