package com.example.leaseplatform.prd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.prd.dto.MenuCreateReq;
import com.example.leaseplatform.prd.dto.MenuUpdateReq;
import com.example.leaseplatform.prd.dto.MenuVO;
import com.example.leaseplatform.prd.entity.PrdDailyMenu;
import com.example.leaseplatform.prd.entity.PrdProduct;
import com.example.leaseplatform.prd.mapper.PrdDailyMenuMapper;
import com.example.leaseplatform.prd.mapper.PrdProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 每日菜单服务（管理端 CRUD + 小程序按日期查询）。
 */
@Service
@RequiredArgsConstructor
public class PrdDailyMenuService {

    private final PrdDailyMenuMapper menuMapper;
    private final PrdProductMapper productMapper;

    /** 管理端分页列表：可按日期 / 套餐筛选 */
    public PageResult<MenuVO> page(int page, int size, LocalDate menuDate, Long productId) {
        Page<PrdDailyMenu> p = new Page<>(PrdCategoryService.normalizePage(page), PrdCategoryService.normalizeSize(size));
        LambdaQueryWrapper<PrdDailyMenu> qw = new LambdaQueryWrapper<PrdDailyMenu>()
                .eq(menuDate != null, PrdDailyMenu::getMenuDate, menuDate)
                .eq(productId != null, PrdDailyMenu::getProductId, productId)
                .orderByAsc(PrdDailyMenu::getMenuDate)
                .orderByAsc(PrdDailyMenu::getProductId)
                .orderByAsc(PrdDailyMenu::getDishType)
                .orderByAsc(PrdDailyMenu::getSortOrder);
        menuMapper.selectPage(p, qw);
        return PageResult.of(p.getTotal(), toVOList(p.getRecords()));
    }

    /** 详情（不存在抛 404） */
    public MenuVO getById(Long id) {
        PrdDailyMenu entity = require(id);
        Map<Long, String> productNames = productMapper.selectBatchIds(List.of(entity.getProductId()))
                .stream().collect(Collectors.toMap(PrdProduct::getId, PrdProduct::getProductName, (a, b) -> a));
        return toVO(entity, productNames);
    }

    public MenuVO create(MenuCreateReq req) {
        requireProduct(req.getProductId());
        PrdDailyMenu entity = new PrdDailyMenu();
        apply(entity, req);
        menuMapper.insert(entity);
        return toVO(entity);
    }

    public MenuVO update(Long id, MenuUpdateReq req) {
        require(id);
        requireProduct(req.getProductId());
        PrdDailyMenu entity = require(id);
        apply(entity, req);
        menuMapper.updateById(entity);
        return toVO(entity);
    }

    public void delete(Long id) {
        require(id);
        menuMapper.deleteById(id);
    }

    /** 小程序公开查询：指定日期（缺省今天）菜单，仅供应中，按套餐/类型/排序 */
    public List<MenuVO> publicList(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        List<PrdDailyMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<PrdDailyMenu>()
                .eq(PrdDailyMenu::getMenuDate, target)
                .eq(PrdDailyMenu::getIsAvailable, 1)
                .orderByAsc(PrdDailyMenu::getProductId)
                .orderByAsc(PrdDailyMenu::getDishType)
                .orderByAsc(PrdDailyMenu::getSortOrder));
        return toVOList(menus);
    }

    private PrdDailyMenu require(Long id) {
        PrdDailyMenu entity = menuMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("菜单项不存在");
        }
        return entity;
    }

    private void requireProduct(Long productId) {
        if (productId != null && productMapper.selectById(productId) == null) {
            throw BizException.notFound("套餐商品不存在");
        }
    }

    private void apply(PrdDailyMenu entity, MenuCreateReq req) {
        entity.setMenuDate(req.getMenuDate());
        entity.setProductId(req.getProductId());
        entity.setDishName(req.getDishName());
        entity.setDishType(req.getDishType());
        entity.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        entity.setIsAvailable(req.getIsAvailable() == null ? 1 : req.getIsAvailable());
    }

    private void apply(PrdDailyMenu entity, MenuUpdateReq req) {
        entity.setMenuDate(req.getMenuDate());
        entity.setProductId(req.getProductId());
        entity.setDishName(req.getDishName());
        entity.setDishType(req.getDishType());
        entity.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        entity.setIsAvailable(req.getIsAvailable() == null ? 1 : req.getIsAvailable());
    }

    private List<MenuVO> toVOList(List<PrdDailyMenu> menus) {
        if (menus.isEmpty()) {
            return List.of();
        }
        Map<Long, String> productNames = productMapper.selectBatchIds(
                        menus.stream().map(PrdDailyMenu::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(PrdProduct::getId, PrdProduct::getProductName, (a, b) -> a));
        return menus.stream().map(m -> toVO(m, productNames)).toList();
    }

    private MenuVO toVO(PrdDailyMenu entity) {
        return toVO(entity, Map.of());
    }

    private MenuVO toVO(PrdDailyMenu entity, Map<Long, String> productNames) {
        MenuVO vo = new MenuVO();
        vo.setId(entity.getId());
        vo.setMenuDate(entity.getMenuDate());
        vo.setProductId(entity.getProductId());
        vo.setProductName(productNames.getOrDefault(entity.getProductId(), null));
        vo.setDishName(entity.getDishName());
        vo.setDishType(entity.getDishType());
        vo.setSortOrder(entity.getSortOrder());
        vo.setIsAvailable(entity.getIsAvailable());
        vo.setCreatedAt(TimeUtil.toEpochMillis(entity.getCreatedAt()));
        vo.setUpdatedAt(TimeUtil.toEpochMillis(entity.getUpdatedAt()));
        return vo;
    }
}
