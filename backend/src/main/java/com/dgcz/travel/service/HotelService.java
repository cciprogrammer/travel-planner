package com.dgcz.travel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dgcz.travel.common.BusinessException;
import com.dgcz.travel.dto.HotelRequest;
import com.dgcz.travel.entity.Hotel;
import com.dgcz.travel.mapper.HotelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 酒店管理（后台维护）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotelService {

    /** 单页最大条数，避免前端传入超大 pageSize 拖垮数据库 */
    private static final int MAX_PAGE_SIZE = 100;

    private final HotelMapper hotelMapper;

    public IPage<Hotel> page(int pageNum, int pageSize, String keyword, String city, Integer status) {
        int num = Math.max(1, pageNum);
        int size = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Hotel> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Hotel::getName, kw)
                    .or().like(Hotel::getArea, kw)
                    .or().like(Hotel::getAddress, kw)
                    .or().like(Hotel::getTags, kw));
        }
        if (StringUtils.hasText(city)) {
            wrapper.eq(Hotel::getCity, city.trim());
        }
        if (status != null) {
            wrapper.eq(Hotel::getStatus, status);
        }
        wrapper.orderByDesc(Hotel::getCreateTime).orderByDesc(Hotel::getId);
        return hotelMapper.selectPage(new Page<>(num, size), wrapper);
    }

    public Hotel create(HotelRequest req) {
        String city = req.getCity().trim();
        String name = req.getName().trim();
        assertNameUnique(city, name, null);

        Hotel hotel = new Hotel();
        apply(hotel, req, city, name);
        if (hotel.getStatus() == null) {
            hotel.setStatus(1);
        }
        hotelMapper.insert(hotel);
        log.info("管理员新增酒店：id={} {}·{}", hotel.getId(), city, name);
        return hotel;
    }

    public Hotel update(Long id, HotelRequest req) {
        Hotel hotel = requireHotel(id);
        String city = req.getCity().trim();
        String name = req.getName().trim();
        assertNameUnique(city, name, id);

        apply(hotel, req, city, name);
        hotelMapper.updateById(hotel);
        return hotelMapper.selectById(id);
    }

    /** 逻辑删除（表内 deleted 字段，数据仍在库里可追溯） */
    public void remove(Long id) {
        requireHotel(id);
        hotelMapper.deleteById(id);
        log.info("管理员删除酒店：id={}", id);
    }

    /** 上架 / 下架 */
    public Hotel updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态只能是 1（上架）或 0（下架）");
        }
        Hotel hotel = requireHotel(id);
        hotel.setStatus(status);
        hotelMapper.updateById(hotel);
        return hotelMapper.selectById(id);
    }

    /** 酒店库里已有的城市列表，供前端筛选下拉使用 */
    public List<String> cities() {
        List<Object> objs = hotelMapper.selectObjs(
                new QueryWrapper<Hotel>().select("DISTINCT city").orderByAsc("city"));
        List<String> list = new ArrayList<>();
        for (Object o : objs) {
            if (o != null) {
                list.add(Objects.toString(o));
            }
        }
        return list;
    }

    /* ------------------------------- 内部方法 ------------------------------- */

    private void apply(Hotel hotel, HotelRequest req, String city, String name) {
        hotel.setName(name);
        hotel.setCity(city);
        hotel.setArea(trimToNull(req.getArea()));
        hotel.setAddress(trimToNull(req.getAddress()));
        hotel.setLevel(trimToNull(req.getLevel()));
        hotel.setPricePerNight(req.getPricePerNight());
        hotel.setRating(req.getRating());
        hotel.setPhone(trimToNull(req.getPhone()));
        hotel.setTags(trimToNull(req.getTags()));
        hotel.setDescription(trimToNull(req.getDescription()));
        hotel.setImageUrl(trimToNull(req.getImageUrl()));
        if (req.getStatus() != null) {
            if (req.getStatus() != 0 && req.getStatus() != 1) {
                throw new BusinessException("状态只能是 1（上架）或 0（下架）");
            }
            hotel.setStatus(req.getStatus());
        }
    }

    private void assertNameUnique(String city, String name, Long excludeId) {
        LambdaQueryWrapper<Hotel> wrapper = new LambdaQueryWrapper<Hotel>()
                .eq(Hotel::getCity, city)
                .eq(Hotel::getName, name);
        if (excludeId != null) {
            wrapper.ne(Hotel::getId, excludeId);
        }
        Long count = hotelMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("「" + city + "」已有同名酒店：" + name);
        }
    }

    private Hotel requireHotel(Long id) {
        Hotel hotel = hotelMapper.selectById(id);
        if (hotel == null) {
            throw new BusinessException(404, "酒店不存在或已被删除");
        }
        return hotel;
    }

    private String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
