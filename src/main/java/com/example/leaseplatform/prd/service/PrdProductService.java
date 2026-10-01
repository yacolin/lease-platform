package com.example.leaseplatform.prd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.common.cache.CacheSpec;
import com.example.leaseplatform.common.cache.MultiLevelCache;
import com.example.leaseplatform.common.cache.ProductBloomRegistry;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品服务（1.3 起 prd_products 收拢为 SPU）：
 * - 管理端 CRUD / 状态生命周期（草稿/待审核/上架/下架/停售）+ 商品创建时自动建默认 SKU；
 * - 小程序公开浏览：仅「上架」商品可见（product_status=2，兼容 1.0 is_available）；
 * - 详情返回 SKU 列表与规格组（PrdSkuService）。
 */
@Service
@RequiredArgsConstructor
public class PrdProductService {

    /** 商品状态生命周期（roadmap 1.3.5） */
    public static final int STATUS_DRAFT = 0;       // 草稿
    public static final int STATUS_PENDING = 1;     // 待审核
    public static final int STATUS_ON_SHELF = 2;    // 上架
    public static final int STATUS_OFF_SHELF = 3;   // 下架
    public static final int STATUS_SUSPENDED = 4;   // 停售

    private final PrdProductMapper productMapper;
    private final PrdCategoryMapper categoryMapper;
    private final PrdDailyMenuMapper menuMapper;
    private final PrdSkuService skuService;
    private final MultiLevelCache cache;
    private final ProductBloomRegistry productBloom;
    private final ObjectMapper objectMapper;

    /** 管理端分页列表：可按分类 / 类型 / 上下架 / 状态 / 名称关键字筛选 */
    public PageResult<ProductVO> page(int page, int size, Long categoryId, Integer productType,
                                      Integer isAvailable, Integer productStatus, String keyword) {
        Page<PrdProduct> p = new Page<>(PrdCategoryService.normalizePage(page), PrdCategoryService.normalizeSize(size));
        LambdaQueryWrapper<PrdProduct> qw = new LambdaQueryWrapper<PrdProduct>()
                .eq(categoryId != null, PrdProduct::getCategoryId, categoryId)
                .eq(productType != null, PrdProduct::getProductType, productType)
                .eq(isAvailable != null, PrdProduct::getIsAvailable, isAvailable)
                .eq(productStatus != null, PrdProduct::getProductStatus, productStatus)
                .like(keyword != null && !keyword.isBlank(), PrdProduct::getProductName, keyword)
                .orderByAsc(PrdProduct::getSortOrder)
                .orderByAsc(PrdProduct::getId);
        productMapper.selectPage(p, qw);
        return PageResult.of(p.getTotal(), toVOList(p.getRecords()));
    }

    /** 详情（不存在抛 404；含 SKU 列表 + 规格组） */
    public ProductVO getById(Long id) {
        PrdProduct entity = require(id);
        Map<Long, String> categoryNames = categoryMapper.selectBatchIds(List.of(entity.getCategoryId()))
                .stream().collect(Collectors.toMap(PrdCategory::getId, PrdCategory::getCategoryName, (a, b) -> a));
        return withSkuDetail(toVO(entity, categoryNames), id);
    }

    /** 创建商品：插入 SPU 并自动创建默认 SKU（无规格，价格=SPU 价格） */
    @Transactional
    public ProductVO create(ProductCreateReq req) {
        requireCategory(req.getCategoryId());
        PrdProduct entity = new PrdProduct();
        apply(entity, req);
        productMapper.insert(entity);
        skuService.createDefault(entity.getId(), entity.getPrice());
        // 布隆只增不删：新建即加入，避免本实例把它判为「一定不存在」
        productBloom.add(entity.getId());
        return toVO(entity);
    }

    @Transactional
    public ProductVO update(Long id, ProductUpdateReq req) {
        require(id);
        requireCategory(req.getCategoryId());
        PrdProduct entity = require(id);
        apply(entity, req);
        productMapper.updateById(entity);
        evictDetail(entity.getId());
        return toVO(entity);
    }

    /**
     * 商品状态（上下架 + 生命周期）：productStatus 优先（0-草稿, 1-待审核, 2-上架, 3-下架, 4-停售），
     * 并联动 is_available（上架=1，其余=0）；只传 isAvailable 时按 1.0 语义同步 product_status（上/下架）。
     */
    public ProductVO updateStatus(Long id, ProductStatusReq req) {
        PrdProduct entity = require(id);
        if (req.getProductStatus() != null) {
            entity.setProductStatus(req.getProductStatus());
            entity.setIsAvailable(req.getProductStatus() == STATUS_ON_SHELF ? 1 : 0);
        } else {
            entity.setIsAvailable(req.getIsAvailable());
            entity.setProductStatus(req.getIsAvailable() != null && req.getIsAvailable() == 1
                    ? STATUS_ON_SHELF : STATUS_OFF_SHELF);
        }
        productMapper.updateById(entity);
        evictDetail(entity.getId());
        return toVO(entity);
    }

    /** 删除商品：被每日菜单引用时拒绝（409）；级联删除其 SKU（订单保留快照） */
    @Transactional
    public void delete(Long id) {
        require(id);
        Long count = menuMapper.selectCount(new LambdaQueryWrapper<PrdDailyMenu>()
                .eq(PrdDailyMenu::getProductId, id));
        if (count != null && count > 0) {
            throw BizException.conflict("该商品已被每日菜单引用，无法删除");
        }
        productMapper.deleteById(id);
        skuService.deleteByProduct(id);
        evictDetail(id);
    }

    /** 小程序公开分页：仅上架商品 */
    public PageResult<ProductVO> publicPage(int page, int size, Long categoryId, Integer productType) {
        Page<PrdProduct> p = new Page<>(PrdCategoryService.normalizePage(page), Math.min(Math.max(size, 1), 100));
        LambdaQueryWrapper<PrdProduct> qw = new LambdaQueryWrapper<PrdProduct>()
                .eq(PrdProduct::getIsAvailable, 1)
                .and(w -> w.isNull(PrdProduct::getProductStatus).or().eq(PrdProduct::getProductStatus, STATUS_ON_SHELF))
                .eq(categoryId != null, PrdProduct::getCategoryId, categoryId)
                .eq(productType != null, PrdProduct::getProductType, productType)
                .orderByAsc(PrdProduct::getSortOrder)
                .orderByAsc(PrdProduct::getId);
        productMapper.selectPage(p, qw);
        return PageResult.of(p.getTotal(), toVOList(p.getRecords()));
    }

    /**
     * 小程序公开详情：仅上架商品，下架视为不存在（404）；含 SKU 列表 + 规格组。
     *
     * <p>这是全项目<b>唯一</b>同时满足「按 ID 点查 + ID 空间稀疏 + 存在匿名探测流量 + 表足够大」
     * 的接口，因此也是唯一值得上布隆过滤器的链路（docs 评估 §5.2）。读路径：
     * <pre>
     *   布隆（一定不存在 → 直接 404，不查库）
     *     ↓ 可能存在
     *   L1 → L2 → DB（null 结果进 60s 空值缓存，防穿透）
     * </pre>
     *
     * <p>空值缓存的必要性：布隆只能拦「从来没存在过」的 ID；
     * 「存在但当前不可见」（已下架 / 已逻辑删除）会落到 DB 并返回 404，
     * 这类请求同样会被反复打，故用短 TTL 空值缓存兜住。
     */
    public ProductVO publicGet(Long id) {
        // 1. 布隆：未预热时 mayExist 恒为 true（放行），保证启动瞬间不误判
        if (!productBloom.mayExist(id)) {
            throw BizException.notFound("商品不存在");
        }
        // 2. L1 → L2 → DB；null 走短 TTL 空值缓存
        ProductVO vo = cache.getOrNull(CacheSpec.PRODUCT_DETAIL,
                MultiLevelCache.l2Key(detailKey(id)), ProductVO.class,
                CacheSpec.PRODUCT_L2_TTL, CacheSpec.PRODUCT_NULL_TTL,
                () -> loadPublicProduct(id));
        if (vo == null) {
            throw BizException.notFound("商品不存在");
        }
        return vo;
    }

    /** 查库并做「仅上架可见」判定；不可见返回 null（由空值缓存兜住，不再抛异常） */
    private ProductVO loadPublicProduct(Long id) {
        PrdProduct entity = productMapper.selectById(id);
        if (entity == null || entity.getIsAvailable() == null || entity.getIsAvailable() != 1
                || (entity.getProductStatus() != null && entity.getProductStatus() != STATUS_ON_SHELF)) {
            return null;
        }
        return withSkuDetail(toVO(entity), id);
    }

    private static String detailKey(Long id) {
        return CacheSpec.PRODUCT_DETAIL + ':' + id;
    }

    /** 商品变更后失效详情缓存（提交后生效） */
    private void evictDetail(Long id) {
        cache.evictAfterCommit(CacheSpec.PRODUCT_DETAIL, MultiLevelCache.l2Key(detailKey(id)));
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
        entity.setProductStatus(req.getIsAvailable() == null || req.getIsAvailable() == 1
                ? STATUS_ON_SHELF : STATUS_OFF_SHELF);
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
        entity.setProductStatus(req.getIsAvailable() == null || req.getIsAvailable() == 1
                ? STATUS_ON_SHELF : STATUS_OFF_SHELF);
        entity.setStock(req.getStock());
        entity.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
    }

    /** 详情补充 SKU 列表与规格组（列表页不携带，JSON non_null 省略） */
    private ProductVO withSkuDetail(ProductVO vo, Long productId) {
        vo.setSkus(skuService.listByProduct(productId));
        vo.setSpecGroups(skuService.listSpecGroups(productId));
        return vo;
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
        vo.setProductStatus(entity.getProductStatus());
        vo.setStock(entity.getStock());
        vo.setSortOrder(entity.getSortOrder());
        vo.setCreatedAt(TimeUtil.toEpochMillis(entity.getCreatedAt()));
        vo.setUpdatedAt(TimeUtil.toEpochMillis(entity.getUpdatedAt()));
        return vo;
    }
}
