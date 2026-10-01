package com.example.leaseplatform.prd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.common.cache.CacheSpec;
import com.example.leaseplatform.common.cache.MultiLevelCache;
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
 * 1.3 起支持二级分类（parent_id）/ 图标（icon_url）/ 展示状态（is_show）。
 */
@Service
@RequiredArgsConstructor
public class PrdCategoryService {

    private final PrdCategoryMapper categoryMapper;
    private final PrdProductMapper productMapper;
    private final MultiLevelCache cache;

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
        apply(entity, req.getCategoryName(), req.getCategoryType(), req.getParentId(),
                req.getSortOrder(), req.getStatus(), req.getIconUrl(), req.getIsShow());
        categoryMapper.insert(entity);
        evictPublicList();
        return toVO(entity);
    }

    public CategoryVO update(Long id, CategoryUpdateReq req) {
        PrdCategory entity = require(id);
        apply(entity, req.getCategoryName(), req.getCategoryType(), req.getParentId(),
                req.getSortOrder(), req.getStatus(), req.getIconUrl(), req.getIsShow());
        categoryMapper.updateById(entity);
        evictPublicList();
        return toVO(entity);
    }

    /** 删除分类：分类下存在未删除商品或子分类时拒绝（409） */
    public void delete(Long id) {
        require(id);
        Long count = productMapper.selectCount(new LambdaQueryWrapper<PrdProduct>()
                .eq(PrdProduct::getCategoryId, id));
        if (count != null && count > 0) {
            throw BizException.conflict("该分类下存在商品，无法删除");
        }
        Long children = categoryMapper.selectCount(new LambdaQueryWrapper<PrdCategory>()
                .eq(PrdCategory::getParentId, id));
        if (children != null && children > 0) {
            throw BizException.conflict("该分类下存在子分类，无法删除");
        }
        categoryMapper.deleteById(id);
        evictPublicList();
    }

    /** 分类变更后失效公开列表缓存（提交后生效） */
    private void evictPublicList() {
        cache.evictAfterCommit(CacheSpec.CATEGORY_LIST, MultiLevelCache.l2Key(CacheSpec.CATEGORY_LIST));
    }

    /**
     * 小程序公开列表：仅启用且展示，按 sort_order 升序。
     *
     * <p>整表缓存（L1 Caffeine + L2 Redis）：分类是典型参照数据，
     * 整表只有几行、写频率极低，而该接口是白名单放行的匿名接口、可被无成本刷量。
     * 原实现每次请求都走一次全表扫描 + filesort（见 docs 评估 §2.1.1）。
     */
    public List<CategoryVO> publicList() {
        return cache.getList(CacheSpec.CATEGORY_LIST, MultiLevelCache.l2Key(CacheSpec.CATEGORY_LIST),
                CategoryVO.class, CacheSpec.L2_TTL,
                () -> categoryMapper.selectList(new LambdaQueryWrapper<PrdCategory>()
                                .eq(PrdCategory::getStatus, 1)
                                .eq(PrdCategory::getIsShow, 1)
                                .orderByAsc(PrdCategory::getSortOrder)
                                .orderByAsc(PrdCategory::getId))
                        .stream().map(this::toVO).toList());
    }

    private PrdCategory require(Long id) {
        PrdCategory entity = categoryMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("分类不存在");
        }
        return entity;
    }

    private void apply(PrdCategory entity, String name, Integer type, Long parentId,
                       Integer sortOrder, Integer status, String iconUrl, Integer isShow) {
        entity.setCategoryName(name);
        entity.setCategoryType(type);
        entity.setParentId(parentId == null ? 0L : parentId);
        entity.setSortOrder(sortOrder == null ? 0 : sortOrder);
        entity.setStatus(status == null ? 1 : status);
        entity.setIconUrl(iconUrl);
        entity.setIsShow(isShow == null ? 1 : isShow);
    }

    private CategoryVO toVO(PrdCategory entity) {
        CategoryVO vo = new CategoryVO();
        vo.setId(entity.getId());
        vo.setCategoryName(entity.getCategoryName());
        vo.setCategoryType(entity.getCategoryType());
        vo.setParentId(entity.getParentId());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        vo.setIconUrl(entity.getIconUrl());
        vo.setIsShow(entity.getIsShow());
        vo.setCreatedAt(TimeUtil.toEpochMillis(entity.getCreatedAt()));
        vo.setUpdatedAt(TimeUtil.toEpochMillis(entity.getUpdatedAt()));
        return vo;
    }

    static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    static int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 1000);
    }
}
