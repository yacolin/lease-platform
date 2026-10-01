package com.example.leaseplatform.prd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.common.cache.CacheSpec;
import com.example.leaseplatform.common.cache.MultiLevelCache;
import com.example.leaseplatform.prd.dto.MenuBatchReq;
import com.example.leaseplatform.prd.dto.MenuCopyReq;
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
    private final MultiLevelCache cache;
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
        evictDate(req.getMenuDate());
        return toVO(entity);
    }

    public MenuVO update(Long id, MenuUpdateReq req) {
        requireProduct(req.getProductId());
        PrdDailyMenu entity = require(id);
        LocalDate oldDate = entity.getMenuDate();
        apply(entity, req);
        menuMapper.updateById(entity);
        // 日期可能被改动：新旧两天的缓存都要失效
        evictDate(oldDate);
        evictDate(req.getMenuDate());
        return toVO(entity);
    }

    public void delete(Long id) {
        PrdDailyMenu entity = require(id);
        menuMapper.deleteById(id);
        evictDate(entity.getMenuDate());
    }

    /** 整单配置：覆盖式替换指定日期的全部菜品（先删后插，事务内） */
    @org.springframework.transaction.annotation.Transactional
    public List<MenuVO> batchCreate(MenuBatchReq req) {
        deleteByDate(req.getMenuDate());
        List<PrdDailyMenu> created = new java.util.ArrayList<>();
        for (MenuBatchReq.MenuBatchItem item : req.getItems()) {
            requireProduct(item.getProductId());
            PrdDailyMenu entity = new PrdDailyMenu();
            entity.setMenuDate(req.getMenuDate());
            entity.setProductId(item.getProductId());
            entity.setDishName(item.getDishName());
            entity.setDishType(item.getDishType());
            entity.setSortOrder(item.getSortOrder() == null ? 0 : item.getSortOrder());
            entity.setIsAvailable(1);
            menuMapper.insert(entity);
            created.add(entity);
        }
        evictDate(req.getMenuDate());
        return toVOList(created);
    }

    /** 复制整单：把 sourceDate 的菜品复制到 targetDate（目标日期先清空） */
    @org.springframework.transaction.annotation.Transactional
    public void copy(MenuCopyReq req) {
        List<PrdDailyMenu> source = menuMapper.selectList(new LambdaQueryWrapper<PrdDailyMenu>()
                .eq(PrdDailyMenu::getMenuDate, req.getSourceDate()));
        if (source.isEmpty()) {
            throw BizException.notFound("源日期没有菜单可复制");
        }
        deleteByDate(req.getTargetDate());
        for (PrdDailyMenu s : source) {
            PrdDailyMenu entity = new PrdDailyMenu();
            entity.setMenuDate(req.getTargetDate());
            entity.setProductId(s.getProductId());
            entity.setDishName(s.getDishName());
            entity.setDishType(s.getDishType());
            entity.setSortOrder(s.getSortOrder());
            entity.setIsAvailable(s.getIsAvailable());
            menuMapper.insert(entity);
        }
    }

    /** 按日期清空整单 */
    public void deleteByDate(LocalDate menuDate) {
        menuMapper.delete(new LambdaQueryWrapper<PrdDailyMenu>()
                .eq(PrdDailyMenu::getMenuDate, menuDate));
        evictDate(menuDate);
    }

    /**
     * 小程序公开查询：指定日期（缺省今天）菜单，仅供应中，按套餐/类型/排序。
     *
     * <p>按<b>日期</b>缓存（L1 + L2）：菜单是按天生成的参照数据，同一天内被反复读取；
     * 原实现每次请求都要扫一遍该日全部菜品并在 toVOList 里回查商品名。
     * 写路径（create/update/delete/batchCreate/copy/deleteByDate）均会失效对应日期。
     */
    public List<MenuVO> publicList(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        return cache.getList(CacheSpec.MENU_LIST, MultiLevelCache.l2Key(menuKey(target)),
                MenuVO.class, CacheSpec.MENU_L2_TTL,
                () -> toVOList(menuMapper.selectList(new LambdaQueryWrapper<PrdDailyMenu>()
                        .eq(PrdDailyMenu::getMenuDate, target)
                        .eq(PrdDailyMenu::getIsAvailable, 1)
                        .orderByAsc(PrdDailyMenu::getProductId)
                        .orderByAsc(PrdDailyMenu::getDishType)
                        .orderByAsc(PrdDailyMenu::getSortOrder))));
    }

    /** 菜单按日期分片缓存；逻辑缓存名统一为 menu:list，L2 key 带日期后缀 */
    private static String menuKey(LocalDate date) {
        return CacheSpec.MENU_LIST + ':' + date;
    }

    /** 失效某一天的公开菜单缓存（提交后生效） */
    private void evictDate(LocalDate date) {
        if (date == null) {
            return;
        }
        cache.evictAfterCommit(CacheSpec.MENU_LIST, MultiLevelCache.l2Key(menuKey(date)));
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
