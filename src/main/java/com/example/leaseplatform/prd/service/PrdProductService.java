package com.example.leaseplatform.prd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.prd.dto.ProductCreateReq;
import com.example.leaseplatform.prd.dto.ProductStatusReq;
import com.example.leaseplatform.prd.dto.ProductUpdateReq;
import com.example.leaseplatform.prd.dto.ProductVO;
import com.example.leaseplatform.prd.entity.PrdCategory;
import com.example.leaseplatform.prd.entity.PrdDailyMenu;
import com.example.leaseplatform.prd.entity.PrdProduct;
import com.example.leaseplatform.prd.mapper.PrdCategoryMapper;
import com.example.leaseplatform.prd.mapper.PrdDailyMenuMapper;
import com.example.leaseplatform.prd.mapper.PrdProductMapper;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品服务（管理端 CRUD / 上下架 + 小程序公开浏览）。
 */
@Service
@RequiredArgsConstructor
public class PrdProductService {

    private final PrdProductMapper productMapper;
    private final PrdCategoryMapper categoryMapper;
    private final PrdDailyMenuMapper menuMapper;
    private final ObjectMapper objectMapper;

    /** 管理端分页列表：可按分类 / 类型 / 上下架 / 名称关键字筛选 */
    public PageResult<ProductVO> page(int page, int size, Long categoryId, Integer productType,
                                      Integer isAvailable, String keyword) {
        Page<PrdProduct> p = new Page<>(PrdCategoryService.normalizePage(page), PrdCategoryService.normalizeSize(size));
        LambdaQueryWrapper<PrdProduct> qw = new LambdaQueryWrapper<PrdProduct>()
                .eq(categoryId != null, PrdProduct::getCategoryId, categoryId)
                .eq(productType != null, PrdProduct::getProductType, productType)
                .eq(isAvailable != null, PrdProduct::getIsAvailable, isAvailable)
                .like(keyword != null && !keyword.isBlank(), PrdProduct::getProductName, keyword)
                .orderByAsc(PrdProduct::getSortOrder)
                .orderByAsc(PrdProduct::getId);
        productMapper.selectPage(p, qw);
        return PageResult.of(p.getTotal(), toVOList(p.getRecords()));
    }

    /** 详情（不存在抛 404） */
    public ProductVO getById(Long id) {
        PrdProduct entity = require(id);
        Map<Long, String> categoryNames = categoryMapper.selectBatchIds(List.of(entity.getCategoryId()))
                .stream().collect(Collectors.toMap(PrdCategory::getId, PrdCategory::getCategoryName, (a, b) -> a));
        return toVO(entity, categoryNames);
    }

    public ProductVO create(ProductCreateReq req) {
        requireCategory(req.getCategoryId());
        PrdProduct entity = new PrdProduct();
        apply(entity, req);
        productMapper.insert(entity);
        return toVO(entity);
    }

    public ProductVO update(Long id, ProductUpdateReq req) {
        require(id);
        requireCategory(req.getCategoryId());
        PrdProduct entity = require(id);
        apply(entity, req);
        productMapper.updateById(entity);
        return toVO(entity);
    }

    /** 上下架（PUT /products/{id}/status） */
    public ProductVO updateStatus(Long id, ProductStatusReq req) {
        PrdProduct entity = require(id);
        entity.setIsAvailable(req.getIsAvailable());
        productMapper.updateById(entity);
        return toVO(entity);
    }

    /** 删除商品：被每日菜单引用时拒绝（409） */
    public void delete(Long id) {
        require(id);
        Long count = menuMapper.selectCount(new LambdaQueryWrapper<PrdDailyMenu>()
                .eq(PrdDailyMenu::getProductId, id));
        if (count != null && count > 0) {
            throw BizException.conflict("该商品已被每日菜单引用，无法删除");
        }
        productMapper.deleteById(id);
    }

    /** 小程序公开分页：仅上架商品 */
    public PageResult<ProductVO> publicPage(int page, int size, Long categoryId, Integer productType) {
        Page<PrdProduct> p = new Page<>(PrdCategoryService.normalizePage(page), Math.min(Math.max(size, 1), 100));
        LambdaQueryWrapper<PrdProduct> qw = new LambdaQueryWrapper<PrdProduct>()
                .eq(PrdProduct::getIsAvailable, 1)
                .eq(categoryId != null, PrdProduct::getCategoryId, categoryId)
                .eq(productType != null, PrdProduct::getProductType, productType)
                .orderByAsc(PrdProduct::getSortOrder)
                .orderByAsc(PrdProduct::getId);
        productMapper.selectPage(p, qw);
        return PageResult.of(p.getTotal(), toVOList(p.getRecords()));
    }

    /** 小程序公开详情：仅上架商品，下架视为不存在（404） */
    public ProductVO publicGet(Long id) {
        PrdProduct entity = productMapper.selectById(id);
        if (entity == null || entity.getIsAvailable() == null || entity.getIsAvailable() != 1) {
            throw BizException.notFound("商品不存在");
        }
        return toVO(entity);
    }

    private PrdProduct require(Long id) {
        PrdProduct entity = productMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("商品不存在");
        }
        return entity;
    }

    private void requireCategory(Long categoryId) {
        if (categoryId != null && categoryMapper.selectById(categoryId) == null) {
            throw BizException.notFound("分类不存在");
        }
    }

    private void apply(PrdProduct entity, ProductCreateReq req) {
        entity.setCategoryId(req.getCategoryId());
        entity.setProductName(req.getProductName());
        entity.setProductType(req.getProductType());
        entity.setPrice(req.getPrice());
        entity.setDescription(req.getDescription());
        entity.setImageUrl(req.getImageUrl());
        entity.setSpecOptions(writeJson(req.getSpecOptions()));
        entity.setIsAvailable(req.getIsAvailable() == null ? 1 : req.getIsAvailable());
        entity.setStock(req.getStock());
        entity.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
    }

    private void apply(PrdProduct entity, ProductUpdateReq req) {
        entity.setCategoryId(req.getCategoryId());
        entity.setProductName(req.getProductName());
        entity.setProductType(req.getProductType());
        entity.setPrice(req.getPrice());
        entity.setDescription(req.getDescription());
        entity.setImageUrl(req.getImageUrl());
        entity.setSpecOptions(writeJson(req.getSpecOptions()));
        entity.setIsAvailable(req.getIsAvailable() == null ? 1 : req.getIsAvailable());
        entity.setStock(req.getStock());
        entity.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
    }

    /** 规格选项：对象 → JSON 字符串（null 保持 null） */
    private String writeJson(Object specOptions) {
        if (specOptions == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(specOptions);
        } catch (Exception e) {
            throw BizException.badRequest("规格选项不是合法 JSON");
        }
    }

    /** JSON 字符串 → 对象（原样返回给前端） */
    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return json;
        }
    }

    private List<ProductVO> toVOList(List<PrdProduct> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, String> categoryNames = categoryMapper.selectBatchIds(
                        products.stream().map(PrdProduct::getCategoryId).distinct().toList())
                .stream().collect(Collectors.toMap(PrdCategory::getId, PrdCategory::getCategoryName, (a, b) -> a));
        return products.stream().map(p -> toVO(p, categoryNames)).toList();
    }

    private ProductVO toVO(PrdProduct entity) {
        return toVO(entity, Map.of());
    }

    private ProductVO toVO(PrdProduct entity, Map<Long, String> categoryNames) {
        ProductVO vo = new ProductVO();
        vo.setId(entity.getId());
        vo.setCategoryId(entity.getCategoryId());
        vo.setCategoryName(categoryNames.getOrDefault(entity.getCategoryId(), null));
        vo.setProductName(entity.getProductName());
        vo.setProductType(entity.getProductType());
        vo.setPrice(entity.getPrice());
        vo.setDescription(entity.getDescription());
        vo.setImageUrl(entity.getImageUrl());
        vo.setSpecOptions(readJson(entity.getSpecOptions()));
        vo.setIsAvailable(entity.getIsAvailable());
        vo.setStock(entity.getStock());
        vo.setSortOrder(entity.getSortOrder());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
