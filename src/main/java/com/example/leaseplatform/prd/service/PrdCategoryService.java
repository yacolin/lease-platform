package com.example.leaseplatform.prd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.prd.dto.CategoryCreateReq;
import com.example.leaseplatform.prd.dto.CategoryUpdateReq;
import com.example.leaseplatform.prd.dto.CategoryVO;
import com.example.leaseplatform.prd.entity.PrdCategory;
import com.example.leaseplatform.prd.entity.PrdProduct;
import com.example.leaseplatform.prd.mapper.PrdCategoryMapper;
import com.example.leaseplatform.prd.mapper.PrdProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品分类服务（管理端 CRUD + 小程序公开列表）。
 */
@Service
@RequiredArgsConstructor
public class PrdCategoryService {

    private final PrdCategoryMapper categoryMapper;
    private final PrdProductMapper productMapper;

    /** 管理端分页列表 */
    public PageResult<CategoryVO> page(int page, int size, Integer categoryType, Integer status) {
        Page<PrdCategory> p = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<PrdCategory> qw = new LambdaQueryWrapper<PrdCategory>()
                .eq(categoryType != null, PrdCategory::getCategoryType, categoryType)
                .eq(status != null, PrdCategory::getStatus, status)
                .orderByAsc(PrdCategory::getSortOrder)
                .orderByAsc(PrdCategory::getId);
        categoryMapper.selectPage(p, qw);
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    /** 详情（不存在抛 404） */
    public CategoryVO getById(Long id) {
        return toVO(require(id));
    }

    public CategoryVO create(CategoryCreateReq req) {
        PrdCategory entity = new PrdCategory();
        apply(entity, req.getCategoryName(), req.getCategoryType(), req.getSortOrder(), req.getStatus());
        categoryMapper.insert(entity);
        return toVO(entity);
    }

    public CategoryVO update(Long id, CategoryUpdateReq req) {
        PrdCategory entity = require(id);
        apply(entity, req.getCategoryName(), req.getCategoryType(), req.getSortOrder(), req.getStatus());
        categoryMapper.updateById(entity);
        return toVO(entity);
    }

    /** 删除分类：分类下存在未删除商品时拒绝（409） */
    public void delete(Long id) {
        require(id);
        Long count = productMapper.selectCount(new LambdaQueryWrapper<PrdProduct>()
                .eq(PrdProduct::getCategoryId, id));
        if (count != null && count > 0) {
            throw BizException.conflict("该分类下存在商品，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    /** 小程序公开列表：仅启用，按 sort_order 升序 */
    public List<CategoryVO> publicList() {
        return categoryMapper.selectList(new LambdaQueryWrapper<PrdCategory>()
                        .eq(PrdCategory::getStatus, 1)
                        .orderByAsc(PrdCategory::getSortOrder)
                        .orderByAsc(PrdCategory::getId))
                .stream().map(this::toVO).toList();
    }

    private PrdCategory require(Long id) {
        PrdCategory entity = categoryMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("分类不存在");
        }
        return entity;
    }

    private void apply(PrdCategory entity, String name, Integer type, Integer sortOrder, Integer status) {
        entity.setCategoryName(name);
        entity.setCategoryType(type);
        entity.setSortOrder(sortOrder == null ? 0 : sortOrder);
        entity.setStatus(status == null ? 1 : status);
    }

    private CategoryVO toVO(PrdCategory entity) {
        CategoryVO vo = new CategoryVO();
        vo.setId(entity.getId());
        vo.setCategoryName(entity.getCategoryName());
        vo.setCategoryType(entity.getCategoryType());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    static int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 1000);
    }
}
