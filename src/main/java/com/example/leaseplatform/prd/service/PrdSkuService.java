package com.example.leaseplatform.prd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.ord.entity.OrdOrderItem;
import com.example.leaseplatform.ord.mapper.OrdOrderItemMapper;
import com.example.leaseplatform.prd.dto.SkuCreateReq;
import com.example.leaseplatform.prd.dto.SkuUpdateReq;
import com.example.leaseplatform.prd.dto.SkuVO;
import com.example.leaseplatform.prd.dto.SpecGroupCreateReq;
import com.example.leaseplatform.prd.dto.SpecGroupUpdateReq;
import com.example.leaseplatform.prd.dto.SpecGroupVO;
import com.example.leaseplatform.prd.dto.SpecValueVO;
import com.example.leaseplatform.prd.entity.PrdProduct;
import com.example.leaseplatform.prd.entity.PrdSku;
import com.example.leaseplatform.prd.entity.PrdSpecGroup;
import com.example.leaseplatform.prd.entity.PrdSpecValue;
import com.example.leaseplatform.prd.mapper.PrdProductMapper;
import com.example.leaseplatform.prd.mapper.PrdSkuMapper;
import com.example.leaseplatform.prd.mapper.PrdSpecGroupMapper;
import com.example.leaseplatform.prd.mapper.PrdSpecValueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SKU 与规格管理服务（1.3 商品中心 SKU 化）：
 * <pre>
 *   prd_products（SPU）
 *        │
 *        ├── prd_spec_groups ── prd_spec_values（规格组合）
 *        └── prd_skus（SKU：价格/库存/规格组合快照）
 * </pre>
 * 规格组/值被 SKU 引用后不允许变更或删除（保证 SKU 规格快照一致）；
 * SKU 被订单明细引用后不允许删除（订单保留快照，见 roadmap 4.6）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrdSkuService {

    /** SKU 状态：0-停售, 1-可售 */
    public static final int STATUS_OFF = 0;
    public static final int STATUS_ON = 1;

    private final PrdSkuMapper skuMapper;
    private final PrdSpecGroupMapper groupMapper;
    private final PrdSpecValueMapper valueMapper;
    private final PrdProductMapper productMapper;
    private final OrdOrderItemMapper orderItemMapper;
    private final ObjectMapper objectMapper;
    /** 公开详情的 VO 里含 skus[].stock 与 specGroups，故 SKU/规格组的所有写操作都要失效详情缓存 */
    private final PrdProductCache productCache;

    // ==================== SKU ====================

    /** 某商品的 SKU 列表（按 sort_order/id 升序） */
    public List<SkuVO> listByProduct(Long productId) {
        requireProduct(productId);
        return skuMapper.selectList(new LambdaQueryWrapper<PrdSku>()
                        .eq(PrdSku::getProductId, productId)
                        .orderByAsc(PrdSku::getSortOrder)
                        .orderByAsc(PrdSku::getId))
                .stream().map(this::toSkuVO).toList();
    }

    /** 创建 SKU（specValueIds 为空 → 默认 SKU，无规格） */
    @Transactional
    public SkuVO create(Long productId, SkuCreateReq req) {
        requireProduct(productId);
        String specValueIdsJson = null;
        String specSnapshotJson = null;
        if (req.getSpecValueIds() != null && !req.getSpecValueIds().isEmpty()) {
            specValueIdsJson = writeJson(req.getSpecValueIds());
            specSnapshotJson = buildSpecSnapshot(productId, req.getSpecValueIds());
        }
        PrdSku sku = new PrdSku();
        sku.setSkuCode(req.getSkuCode() == null || req.getSkuCode().isBlank()
                ? generateSkuCode(productId) : req.getSkuCode());
        sku.setProductId(productId);
        sku.setSpecValueIds(specValueIdsJson);
        sku.setSpecSnapshot(specSnapshotJson);
        sku.setPrice(req.getPrice());
        sku.setCostPrice(req.getCostPrice());
        sku.setStock(req.getStock());
        sku.setStatus(req.getStatus() == null ? STATUS_ON : req.getStatus());
        sku.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        try {
            skuMapper.insert(sku);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw BizException.conflict("SKU 编码重复：" + sku.getSkuCode());
        }
        productCache.evictDetail(productId);
        return toSkuVO(sku);
    }

    /** 更新 SKU（specValueIds 非空才变更规格组合并重建快照） */
    @Transactional
    public SkuVO update(Long productId, Long skuId, SkuUpdateReq req) {
        PrdSku sku = requireSku(productId, skuId);
        if (req.getSkuCode() != null && !req.getSkuCode().isBlank()) {
            sku.setSkuCode(req.getSkuCode());
        }
        if (req.getSpecValueIds() != null) {
            if (req.getSpecValueIds().isEmpty()) {
                sku.setSpecValueIds(null);
                sku.setSpecSnapshot(null);
            } else {
                sku.setSpecValueIds(writeJson(req.getSpecValueIds()));
                sku.setSpecSnapshot(buildSpecSnapshot(productId, req.getSpecValueIds()));
            }
        }
        if (req.getPrice() != null) {
            sku.setPrice(req.getPrice());
        }
        if (req.getCostPrice() != null) {
            sku.setCostPrice(req.getCostPrice());
        }
        if (req.getStock() != null) {
            sku.setStock(req.getStock());
        }
        if (req.getStatus() != null) {
            sku.setStatus(req.getStatus());
        }
        if (req.getSortOrder() != null) {
            sku.setSortOrder(req.getSortOrder());
        }
        try {
            skuMapper.updateById(sku);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw BizException.conflict("SKU 编码重复：" + sku.getSkuCode());
        }
        productCache.evictDetail(productId);
        return toSkuVO(sku);
    }

    /** 删除 SKU：被订单明细引用时拒绝（订单保留 SKU 快照） */
    @Transactional
    public void delete(Long productId, Long skuId) {
        requireSku(productId, skuId);
        Long ref = orderItemMapper.selectCount(new LambdaQueryWrapper<OrdOrderItem>()
                .eq(OrdOrderItem::getSkuId, skuId));
        if (ref != null && ref > 0) {
            throw BizException.conflict("该 SKU 已被订单引用，无法删除");
        }
        skuMapper.deleteById(skuId);
        productCache.evictDetail(productId);
    }

    /** SKU 上下架 */
    @Transactional
    public SkuVO updateStatus(Long productId, Long skuId, Integer status) {
        PrdSku sku = requireSku(productId, skuId);
        sku.setStatus(status);
        skuMapper.updateById(sku);
        productCache.evictDetail(productId);
        return toSkuVO(sku);
    }

    /** 下单解析：指定 SKU 必须属于该商品且可售；未指定 → 默认 SKU（首个可售，可能为 null） */
    public PrdSku resolveForOrder(Long productId, Long skuId) {
        if (skuId == null) {
            return resolveDefaultSku(productId);
        }
        PrdSku sku = skuMapper.selectById(skuId);
        if (sku == null || !sku.getProductId().equals(productId)) {
            throw BizException.badRequest("SKU 不存在");
        }
        if (sku.getStatus() == null || sku.getStatus() != STATUS_ON) {
            throw BizException.badRequest("该 SKU 已停售");
        }
        return sku;
    }

    /** 默认 SKU：该商品首个可售 SKU（无则 null，下单回落 SPU 价格） */
    public PrdSku resolveDefaultSku(Long productId) {
        return skuMapper.selectOne(new LambdaQueryWrapper<PrdSku>()
                .eq(PrdSku::getProductId, productId)
                .eq(PrdSku::getStatus, STATUS_ON)
                .orderByAsc(PrdSku::getSortOrder)
                .orderByAsc(PrdSku::getId)
                .last("LIMIT 1"));
    }

    /** 创建默认 SKU（商品创建时使用；无规格，价格=SPU 价格） */
    @Transactional
    public PrdSku createDefault(Long productId, long price) {
        PrdSku sku = new PrdSku();
        sku.setSkuCode(generateSkuCode(productId));
        sku.setProductId(productId);
        sku.setPrice(price);
        sku.setStatus(STATUS_ON);
        sku.setSortOrder(0);
        skuMapper.insert(sku);
        productCache.evictDetail(productId);
        return sku;
    }

    /** 删除商品下全部 SKU（商品删除级联；订单保留快照不受影响） */
    @Transactional
    public void deleteByProduct(Long productId) {
        skuMapper.delete(new LambdaQueryWrapper<PrdSku>().eq(PrdSku::getProductId, productId));
        productCache.evictDetail(productId);
    }

    // ==================== 规格组 / 规格值 ====================

    /** 某商品的规格组列表（含规格值，按 sort_order/id 升序） */
    public List<SpecGroupVO> listSpecGroups(Long productId) {
        requireProduct(productId);
        List<PrdSpecGroup> groups = groupMapper.selectList(new LambdaQueryWrapper<PrdSpecGroup>()
                .eq(PrdSpecGroup::getProductId, productId)
                .orderByAsc(PrdSpecGroup::getSortOrder)
                .orderByAsc(PrdSpecGroup::getId));
        if (groups.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PrdSpecValue>> valuesByGroup = valueMapper.selectList(
                        new LambdaQueryWrapper<PrdSpecValue>()
                                .in(PrdSpecValue::getGroupId, groups.stream().map(PrdSpecGroup::getId).toList())
                                .orderByAsc(PrdSpecValue::getSortOrder)
                                .orderByAsc(PrdSpecValue::getId))
                .stream().collect(Collectors.groupingBy(PrdSpecValue::getGroupId));
        return groups.stream().map(g -> toGroupVO(g, valuesByGroup.getOrDefault(g.getId(), List.of()))).toList();
    }

    /** 创建规格组（可携带规格值） */
    @Transactional
    public SpecGroupVO createSpecGroup(Long productId, SpecGroupCreateReq req) {
        requireProduct(productId);
        if (groupMapper.selectCount(new LambdaQueryWrapper<PrdSpecGroup>()
                .eq(PrdSpecGroup::getProductId, productId)
                .eq(PrdSpecGroup::getGroupName, req.getGroupName())) > 0) {
            throw BizException.conflict("该规格组已存在：" + req.getGroupName());
        }
        PrdSpecGroup group = new PrdSpecGroup();
        group.setProductId(productId);
        group.setGroupName(req.getGroupName());
        group.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        groupMapper.insert(group);
        List<PrdSpecValue> values = insertValues(group.getId(), req.getValues());
        productCache.evictDetail(productId);
        return toGroupVO(group, values);
    }

    /** 更新规格组（values 全量替换；已被 SKU 引用时拒绝，保证 SKU 快照一致） */
    @Transactional
    public SpecGroupVO updateSpecGroup(Long productId, Long groupId, SpecGroupUpdateReq req) {
        PrdSpecGroup group = requireGroup(productId, groupId);
        ensureGroupNotReferenced(groupId, "更新");
        group.setGroupName(req.getGroupName());
        if (req.getSortOrder() != null) {
            group.setSortOrder(req.getSortOrder());
        }
        groupMapper.updateById(group);
        // 全量替换规格值
        valueMapper.delete(new LambdaQueryWrapper<PrdSpecValue>().eq(PrdSpecValue::getGroupId, groupId));
        List<PrdSpecValue> values = insertValues(groupId, req.getValues());
        productCache.evictDetail(productId);
        return toGroupVO(group, values);
    }

    /** 删除规格组（连同规格值；已被 SKU 引用时拒绝） */
    @Transactional
    public void deleteSpecGroup(Long productId, Long groupId) {
        requireGroup(productId, groupId);
        ensureGroupNotReferenced(groupId, "删除");
        valueMapper.delete(new LambdaQueryWrapper<PrdSpecValue>().eq(PrdSpecValue::getGroupId, groupId));
        groupMapper.deleteById(groupId);
        productCache.evictDetail(productId);
    }

    // ==================== 内部 ====================

    /** 规格值 ID 组合 → 校验归属同一商品 → 规格快照 JSON {组名: 值名} */
    private String buildSpecSnapshot(Long productId, List<Long> specValueIds) {
        Map<Long, PrdSpecValue> values = valueMapper.selectBatchIds(specValueIds).stream()
                .collect(Collectors.toMap(PrdSpecValue::getId, Function.identity()));
        if (values.size() != specValueIds.size()) {
            throw BizException.badRequest("规格值不存在");
        }
        Map<Long, PrdSpecGroup> groups = groupMapper.selectBatchIds(
                        values.values().stream().map(PrdSpecValue::getGroupId).distinct().toList())
                .stream().collect(Collectors.toMap(PrdSpecGroup::getId, Function.identity()));
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (Long valueId : specValueIds) {
            PrdSpecValue value = values.get(valueId);
            PrdSpecGroup group = groups.get(value.getGroupId());
            if (group == null || !group.getProductId().equals(productId)) {
                throw BizException.badRequest("规格值不属于该商品");
            }
            snapshot.put(group.getGroupName(), value.getValueName());
        }
        return writeJson(snapshot);
    }

    private List<PrdSpecValue> insertValues(Long groupId, List<String> valueNames) {
        if (valueNames == null || valueNames.isEmpty()) {
            return List.of();
        }
        List<PrdSpecValue> inserted = new ArrayList<>();
        int idx = 1;
        for (String name : valueNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            PrdSpecValue value = new PrdSpecValue();
            value.setGroupId(groupId);
            value.setValueName(name.trim());
            value.setSortOrder(idx++);
            try {
                valueMapper.insert(value);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw BizException.conflict("规格值重复：" + name);
            }
            inserted.add(value);
        }
        return inserted;
    }

    /** 规格组是否已被 SKU 引用（引用后禁止变更/删除，保证快照一致；LIKE 匹配可能误伤，保守拒绝） */
    private void ensureGroupNotReferenced(Long groupId, String action) {
        List<Long> valueIds = valueMapper.selectList(new LambdaQueryWrapper<PrdSpecValue>()
                        .eq(PrdSpecValue::getGroupId, groupId))
                .stream().map(PrdSpecValue::getId).toList();
        for (Long vid : valueIds) {
            Long ref = skuMapper.selectCount(new LambdaQueryWrapper<PrdSku>()
                    .like(PrdSku::getSpecValueIds, String.valueOf(vid)));
            if (ref != null && ref > 0) {
                throw BizException.conflict("该规格组已被 SKU 引用，无法" + action);
            }
        }
    }

    private PrdSku requireSku(Long productId, Long skuId) {
        PrdSku sku = skuMapper.selectById(skuId);
        if (sku == null || !sku.getProductId().equals(productId)) {
            throw BizException.notFound("SKU 不存在");
        }
        return sku;
    }

    private PrdSpecGroup requireGroup(Long productId, Long groupId) {
        PrdSpecGroup group = groupMapper.selectById(groupId);
        if (group == null || !group.getProductId().equals(productId)) {
            throw BizException.notFound("规格组不存在");
        }
        return group;
    }

    private PrdProduct requireProduct(Long productId) {
        PrdProduct product = productMapper.selectById(productId);
        if (product == null) {
            throw BizException.notFound("商品不存在");
        }
        return product;
    }

    /** 自动生成唯一 SKU 编码：SKU{商品id:04d}{序号:02d} */
    private String generateSkuCode(Long productId) {
        long n = skuMapper.selectCount(new LambdaQueryWrapper<PrdSku>()
                .eq(PrdSku::getProductId, productId)) + 1;
        for (int i = 0; i < 100; i++, n++) {
            String code = String.format("SKU%04d%02d", productId, n);
            Long dup = skuMapper.selectCount(new LambdaQueryWrapper<PrdSku>()
                    .eq(PrdSku::getSkuCode, code));
            if (dup == null || dup == 0) {
                return code;
            }
        }
        throw BizException.conflict("SKU 编码生成失败，请重试");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw BizException.badRequest("JSON 序列化失败");
        }
    }

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

    private SkuVO toSkuVO(PrdSku sku) {
        SkuVO vo = new SkuVO();
        vo.setId(sku.getId());
        vo.setSkuCode(sku.getSkuCode());
        vo.setProductId(sku.getProductId());
        vo.setSpecValueIds(readJson(sku.getSpecValueIds()));
        vo.setSpecSnapshot(readJson(sku.getSpecSnapshot()));
        vo.setPrice(sku.getPrice());
        vo.setCostPrice(sku.getCostPrice());
        vo.setStock(sku.getStock());
        vo.setStatus(sku.getStatus());
        vo.setSortOrder(sku.getSortOrder());
        vo.setCreatedAt(TimeUtil.toEpochMillis(sku.getCreatedAt()));
        vo.setUpdatedAt(TimeUtil.toEpochMillis(sku.getUpdatedAt()));
        return vo;
    }

    private SpecGroupVO toGroupVO(PrdSpecGroup group, List<PrdSpecValue> values) {
        SpecGroupVO vo = new SpecGroupVO();
        vo.setId(group.getId());
        vo.setProductId(group.getProductId());
        vo.setGroupName(group.getGroupName());
        vo.setSortOrder(group.getSortOrder());
        vo.setValues(values.stream().map(v -> {
            SpecValueVO valueVO = new SpecValueVO();
            valueVO.setId(v.getId());
            valueVO.setValueName(v.getValueName());
            valueVO.setSortOrder(v.getSortOrder());
            return valueVO;
        }).toList());
        return vo;
    }
}
