package com.example.leaseplatform.prd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.cache.MultiLevelCache;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.prd.dto.MenuCreateReq;
import com.example.leaseplatform.prd.dto.MenuVO;
import com.example.leaseplatform.prd.entity.PrdDailyMenu;
import com.example.leaseplatform.prd.entity.PrdProduct;
import com.example.leaseplatform.prd.mapper.PrdDailyMenuMapper;
import com.example.leaseplatform.prd.mapper.PrdProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 每日菜单服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PrdDailyMenuServiceTest {

    @Mock
    private PrdDailyMenuMapper menuMapper;
    @Mock
    private PrdProductMapper productMapper;

    @Mock
    private MultiLevelCache cache;

    private PrdDailyMenuService service;

    @BeforeEach
    void setUp() {
        service = new PrdDailyMenuService(menuMapper, cache, productMapper);
        // MultiLevelCache 为透传 mock：直接调用 loader，使既有断言仍校验真实查询与映射逻辑
        // 用 lenient：只有部分用例会走到缓存读取路径
        lenient().when(cache.getList(anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(4)).get());

    }

    private PrdDailyMenu menu(Long id, LocalDate date, Long productId, String dishName) {
        PrdDailyMenu m = new PrdDailyMenu();
        m.setId(id);
        m.setMenuDate(date);
        m.setProductId(productId);
        m.setDishName(dishName);
        m.setDishType(1);
        m.setSortOrder(1);
        m.setIsAvailable(1);
        return m;
    }

    private MenuCreateReq createReq() {
        MenuCreateReq req = new MenuCreateReq();
        req.setMenuDate(LocalDate.of(2026, 8, 30));
        req.setProductId(4L);
        req.setDishName("红烧肉");
        req.setDishType(1);
        return req;
    }

    @Test
    void create_shouldInsert() {
        when(productMapper.selectById(4L)).thenReturn(new PrdProduct());
        when(menuMapper.insert(any(PrdDailyMenu.class))).thenAnswer(inv -> {
            PrdDailyMenu m = inv.getArgument(0);
            m.setId(1L);
            return 1;
        });

        MenuVO vo = service.create(createReq());

        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getDishName()).isEqualTo("红烧肉");
    }

    @Test
    void create_missingProduct_shouldThrow404() {
        when(productMapper.selectById(99L)).thenReturn(null);
        MenuCreateReq req = createReq();
        req.setProductId(99L);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BizException.class)
                .hasMessage("套餐商品不存在");
        verify(menuMapper, never()).insert(any(PrdDailyMenu.class));
    }

    @Test
    void getById_missing_shouldThrow404() {
        when(menuMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.getById(1L))
                .isInstanceOf(BizException.class)
                .hasMessage("菜单项不存在");
    }

    @Test
    void getById_shouldFillProductName() {
        LocalDate date = LocalDate.of(2026, 8, 30);
        when(menuMapper.selectById(1L)).thenReturn(menu(1L, date, 4L, "红烧肉"));
        PrdProduct p = new PrdProduct();
        p.setId(4L);
        p.setProductName("3荤1素套餐");
        when(productMapper.selectBatchIds(List.of(4L))).thenReturn(List.of(p));

        MenuVO vo = service.getById(1L);

        assertThat(vo.getProductName()).isEqualTo("3荤1素套餐");
    }

    @Test
    void delete_shouldDelete() {
        when(menuMapper.selectById(1L)).thenReturn(menu(1L, LocalDate.of(2026, 8, 30), 4L, "红烧肉"));

        service.delete(1L);

        verify(menuMapper).deleteById(1L);
    }

    @Test
    void page_shouldReturnTotalAndList() {
        LocalDate date = LocalDate.of(2026, 8, 30);
        when(menuMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            Page<PrdDailyMenu> p = inv.getArgument(0);
            p.setRecords(List.of(menu(1L, date, 4L, "红烧肉")));
            p.setTotal(1);
            return p;
        });

        PageResult<MenuVO> result = service.page(1, 10, date, null);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list().get(0).getDishName()).isEqualTo("红烧肉");
    }

    @Test
    void publicList_shouldFillProductName() {
        LocalDate date = LocalDate.of(2026, 8, 30);
        when(menuMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(menu(1L, date, 4L, "红烧肉"), menu(2L, date, 4L, "清炒时蔬")));
        PrdProduct p = new PrdProduct();
        p.setId(4L);
        p.setProductName("3荤1素套餐");
        when(productMapper.selectBatchIds(List.of(4L))).thenReturn(List.of(p));

        List<MenuVO> list = service.publicList(date);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getProductName()).isEqualTo("3荤1素套餐");
    }
}
