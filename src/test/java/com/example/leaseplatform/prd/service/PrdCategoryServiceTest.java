package com.example.leaseplatform.prd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.cache.MultiLevelCache;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.prd.dto.CategoryCreateReq;
import com.example.leaseplatform.prd.dto.CategoryUpdateReq;
import com.example.leaseplatform.prd.dto.CategoryVO;
import com.example.leaseplatform.prd.entity.PrdCategory;
import com.example.leaseplatform.prd.mapper.PrdCategoryMapper;
import com.example.leaseplatform.prd.mapper.PrdProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * 商品分类服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PrdCategoryServiceTest {

    @Mock
    private PrdCategoryMapper categoryMapper;
    @Mock
    private PrdProductMapper productMapper;

    @Mock
    private MultiLevelCache cache;

    private PrdCategoryService service;

    @BeforeEach
    void setUp() {
        service = new PrdCategoryService(categoryMapper, productMapper, cache);
        // MultiLevelCache 为透传 mock：直接调用 loader，使既有断言仍校验真实查询与映射逻辑
        // 用 lenient：只有部分用例会走到缓存读取路径
        lenient().when(cache.getList(anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(4)).get());

    }

    private PrdCategory category(Long id, String name, Integer type) {
        PrdCategory c = new PrdCategory();
        c.setId(id);
        c.setCategoryName(name);
        c.setCategoryType(type);
        c.setSortOrder(1);
        c.setStatus(1);
        return c;
    }

    @Test
    void create_shouldInsertAndReturnVo() {
        when(categoryMapper.insert(any(PrdCategory.class))).thenAnswer(inv -> {
            PrdCategory c = inv.getArgument(0);
            c.setId(10L);
            return 1;
        });
        CategoryCreateReq req = new CategoryCreateReq();
        req.setCategoryName("咖啡");
        req.setCategoryType(1);

        CategoryVO vo = service.create(req);

        assertThat(vo.getId()).isEqualTo(10L);
        assertThat(vo.getCategoryName()).isEqualTo("咖啡");
        assertThat(vo.getCategoryType()).isEqualTo(1);
        verify(categoryMapper).insert(any(PrdCategory.class));
    }

    @Test
    void create_withSubCategoryFields_shouldPersist() {
        // 1.3：二级分类（parentId）/ 图标 / 展示状态
        when(categoryMapper.insert(any(PrdCategory.class))).thenAnswer(inv -> {
            PrdCategory c = inv.getArgument(0);
            c.setId(11L);
            return 1;
        });
        CategoryCreateReq req = new CategoryCreateReq();
        req.setCategoryName("手冲");
        req.setCategoryType(1);
        req.setParentId(1L);
        req.setIconUrl("http://x/icon.png");
        req.setIsShow(0);

        CategoryVO vo = service.create(req);

        ArgumentCaptor<PrdCategory> captor = ArgumentCaptor.forClass(PrdCategory.class);
        verify(categoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getParentId()).isEqualTo(1L);
        assertThat(captor.getValue().getIconUrl()).isEqualTo("http://x/icon.png");
        assertThat(captor.getValue().getIsShow()).isZero();
        assertThat(vo.getParentId()).isEqualTo(1L);
    }

    @Test
    void delete_withChildren_shouldConflict() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, "咖啡", 1));
        when(productMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(categoryMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("子分类");
    }

    @Test
    void getById_shouldReturnVo() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, "咖啡", 1));

        CategoryVO vo = service.getById(1L);

        assertThat(vo.getCategoryName()).isEqualTo("咖啡");
    }

    @Test
    void getById_missing_shouldThrow404() {
        when(categoryMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BizException.class)
                .hasMessage("分类不存在");
    }

    @Test
    void update_shouldPersistAndReturnVo() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, "咖啡", 1));
        CategoryUpdateReq req = new CategoryUpdateReq();
        req.setCategoryName("正餐");
        req.setCategoryType(2);

        CategoryVO vo = service.update(1L, req);

        assertThat(vo.getCategoryName()).isEqualTo("正餐");
        verify(categoryMapper).updateById(any(PrdCategory.class));
    }

    @Test
    void update_missing_shouldThrow404() {
        when(categoryMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(1L, new CategoryUpdateReq()))
                .isInstanceOf(BizException.class)
                .hasMessage("分类不存在");
    }

    @Test
    void delete_withProducts_shouldConflictAndNotDelete() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, "咖啡", 1));
        when(productMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法删除");
        verify(categoryMapper, never()).deleteById(any(PrdCategory.class));
    }

    @Test
    void delete_withoutProducts_shouldDelete() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, "咖啡", 1));
        when(productMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.delete(1L);

        verify(categoryMapper).deleteById(1L);
    }

    @Test
    void page_shouldReturnTotalAndList() {
        when(categoryMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            Page<PrdCategory> p = inv.getArgument(0);
            p.setRecords(List.of(category(1L, "咖啡", 1)));
            p.setTotal(1);
            return p;
        });

        PageResult<CategoryVO> result = service.page(1, 10, null, null);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list()).hasSize(1);
        assertThat(result.list().get(0).getCategoryName()).isEqualTo("咖啡");
    }

    @Test
    void publicList_shouldReturnAll() {
        when(categoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(category(1L, "咖啡", 1), category(2L, "正餐", 2)));

        List<CategoryVO> list = service.publicList();

        assertThat(list).hasSize(2);
        assertThat(list.get(1).getCategoryName()).isEqualTo("正餐");
    }
}
