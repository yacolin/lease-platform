package com.example.leaseplatform.prd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private PrdCategoryService service;

    @BeforeEach
    void setUp() {
        service = new PrdCategoryService(categoryMapper, productMapper);
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
