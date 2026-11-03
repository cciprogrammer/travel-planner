package com.dgcz.travel.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dgcz.travel.common.BusinessException;
import com.dgcz.travel.dto.HotelRequest;
import com.dgcz.travel.entity.Hotel;
import com.dgcz.travel.mapper.HotelMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 白盒测试：酒店管理的校验分支（在城市+同名去重、状态取值、分页上限）。
 */
class HotelServiceTest {

    private final HotelMapper hotelMapper = mock(HotelMapper.class);
    private final HotelService service = new HotelService(hotelMapper);

    private HotelRequest req(String name, String city, int price, Integer status) {
        HotelRequest r = new HotelRequest();
        r.setName(name);
        r.setCity(city);
        r.setPricePerNight(price);
        r.setStatus(status);
        return r;
    }

    private Hotel existing(long id) {
        Hotel h = new Hotel();
        h.setId(id);
        h.setName("旧酒店");
        h.setCity("长沙");
        h.setStatus(1);
        return h;
    }

    @Test
    @DisplayName("新增酒店：默认上架，名称与城市去首尾空格")
    void createTrimsAndDefaultsStatus() {
        when(hotelMapper.selectCount(any())).thenReturn(0L);
        when(hotelMapper.insert(any(Hotel.class))).thenAnswer(inv -> {
            Hotel h = inv.getArgument(0);
            h.setId(4L);
            return 1;
        });

        Hotel created = service.create(req("  测试酒店  ", "  长沙  ", 399, null));

        assertEquals("测试酒店", created.getName());
        assertEquals("长沙", created.getCity());
        assertEquals(1, created.getStatus(), "未传状态时应默认上架");
        assertEquals(4L, created.getId());
    }

    @Test
    @DisplayName("同城同名酒店 → 400「已有同名酒店」")
    void duplicateNameRejected() {
        when(hotelMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(req("测试酒店", "长沙", 399, null)));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("已有同名酒店"));
    }

    @Test
    @DisplayName("非法状态值 → 400")
    void invalidStatusOnCreate() {
        when(hotelMapper.selectCount(any())).thenReturn(0L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(req("测试酒店", "长沙", 399, 9)));
        assertTrue(ex.getMessage().contains("状态只能是"));
    }

    @Test
    @DisplayName("上架/下架：非法值直接拒绝")
    void invalidStatusOnToggle() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateStatus(1L, 5));
        assertTrue(ex.getMessage().contains("状态只能是"));
    }

    @Test
    @DisplayName("操作不存在的酒店 → 404")
    void notFound() {
        when(hotelMapper.selectById(999L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.remove(999L));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("分页参数收敛：pageSize=10000 被压到上限 100，pageNum=0 归一到 1")
    void pageSizeIsClamped() {
        long[] captured = new long[2];
        when(hotelMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<Hotel> p = inv.getArgument(0);
            captured[0] = p.getCurrent();
            captured[1] = p.getSize();
            return p;
        });

        service.page(0, 10000, null, null, null);

        assertEquals(1L, captured[0], "pageNum 应归一到 1");
        assertEquals(100L, captured[1], "pageSize 应被限制在 100");
    }
}
