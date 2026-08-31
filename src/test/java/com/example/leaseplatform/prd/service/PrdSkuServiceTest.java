package com.example.leaseplatform.prd.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.ord.entity.OrdOrderItem;
import com.example.leaseplatform.ord.mapper.OrdOrderItemMapper;
import com.example.leaseplatform.prd.dto.SkuCreateReq;
import com.example.leaseplatform.prd.dto.SkuVO;
import com.example.leaseplatform.prd.dto.SpecGroupCreateReq;
import com.example.leaseplatform.prd.dto.SpecGroupVO;
import com.example.leaseplatform.prd.entity.PrdProduct;
import com.example.leaseplatform.prd.entity.PrdSku;
import com.example.leaseplatform.prd.entity.PrdSpecGroup;
import com.example.leaseplatform.prd.entity.PrdSpecValue;
import com.example.leaseplatform.prd.mapper.PrdProductMapper;
import com.example.leaseplatform.prd.mapper.PrdSkuMapper;
import com.example.leaseplatform.prd.mapper.PrdSpecGroupMapper;
import com.example.leaseplatform.prd.mapper.PrdSpecValueMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SKU 与规格管理服务单元测试（1.3）：SKU 创建/规格快照/删除引用保护/规格组引用保护/下单解析。
 */
@ExtendWith(MockitoExtension.class)
class PrdSkuServiceTest {

    @Mock
    private PrdSkuMapper skuMapper;
    @Mock
    private PrdSpecGroupMapper groupMapper;
    @Mock
    private PrdSpecValueMapper valueMapper;
    @Mock
    private PrdProductMapper productMapper;
    @Mock
    private OrdOrderItemMapper orderItemMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PrdSkuService service;

    @BeforeAll
    static void initMpEntityCache() {
        initTableInfo(PrdSku.class);
        initTableInfo(PrdSpecGroup.class);
        initTableInfo(PrdSpecValue.class);
        initTableInfo(OrdOrderItem.class);
    }

    private static void initTableInfo(Class<?> clazz) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
    }

    @BeforeEach
    void setUp() {
        service = new PrdSkuService(skuMapper, groupMapper, valueMapper, productMapper,
                orderItemMapper, objectMapper);
    }

    private PrdProduct product(Long id) {
        PrdProduct p = new PrdProduct();
        p.setId(id);
        p.setProductName("美式");
        return p;
    }

    private PrdSpecGroup group(Long id, Long productId, String name) {
        PrdSpecGroup g = new PrdSpecGroup();
        g.setId(id);
        g.setProductId(productId);
        g.setGroupName(name);
        g.setSortOrder(1);
        return g;
    }

    private PrdSpecValue value(Long id, Long groupId, String name) {
        PrdSpecValue v = new PrdSpecValue();
        v.setId(id);
        v.setGroupId(groupId);
        v.setValueName(name);
        v.setSortOrder(1);
        return v;
    }

    @Test
    void create_withSpecValueIds_shouldBuildSnapshot() {
        when(productMapper.selectById(1L)).thenReturn(product(1L));
        when(skuMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(valueMapper.selectBatchIds(List.of(1L, 3L)))
                .thenReturn(List.of(value(1L, 10L, "大杯"), value(3L, 20L, "热")));
        when(groupMapper.selectBatchIds(List.of(10L, 20L)))
                .thenReturn(List.of(group(10L, 1L, "杯型"), group(20L, 1L, "温度")));
        when(skuMapper.insert(any(PrdSku.class))).thenReturn(1);

        SkuCreateReq req = new SkuCreateReq();
        req.setSpecValueIds(List.of(1L, 3L));
        req.setPrice(1200L);
        SkuVO vo = service.create(1L, req);

        ArgumentCaptor<PrdSku> captor = ArgumentCaptor.forClass(PrdSku.class);
        verify(skuMapper).insert(captor.capture());
        // 规格快照 {组名: 值名}，规格值 ID 组合入库
        assertThat(captor.getValue().getSpecSnapshot()).contains("杯型").contains("大杯").contains("热");
        assertThat(captor.getValue().getSpecValueIds()).contains("[1,3]");
        assertThat(vo.getSpecSnapshot()).isNotNull();
    }

    @Test
    void create_withoutSpec_shouldInsertDefaultSku() {
        when(productMapper.selectById(1L)).thenReturn(product(1L));
        when(skuMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(skuMapper.insert(any(PrdSku.class))).thenReturn(1);

        SkuCreateReq req = new SkuCreateReq();
        req.setPrice(1500L);
        service.create(1L, req);

        ArgumentCaptor<PrdSku> captor = ArgumentCaptor.forClass(PrdSku.class);
        verify(skuMapper).insert(captor.capture());
        assertThat(captor.getValue().getSpecValueIds()).isNull();
        assertThat(captor.getValue().getSkuCode()).startsWith("SKU");
    }

    @Test
    void create_specValueNotBelongProduct_should400() {
        when(productMapper.selectById(1L)).thenReturn(product(1L));
        when(valueMapper.selectBatchIds(List.of(9L))).thenReturn(List.of(value(9L, 30L, "其他")));
        when(groupMapper.selectBatchIds(List.of(30L))).thenReturn(List.of(group(30L, 2L, "杯型"))); // 属于商品 2

        SkuCreateReq req = new SkuCreateReq();
        req.setSpecValueIds(List.of(9L));
        req.setPrice(100L);

        assertThatThrownBy(() -> service.create(1L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不属于该商品");
    }

    @Test
    void delete_referencedByOrder_shouldConflict() {
        when(skuMapper.selectById(5L)).thenReturn(sku(5L, 1L));
        when(orderItemMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(1L, 5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("订单引用");
        verify(skuMapper, never()).deleteById(anyLong());
    }

    @Test
    void delete_notReferenced_shouldDelete() {
        when(skuMapper.selectById(5L)).thenReturn(sku(5L, 1L));
        when(orderItemMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        service.delete(1L, 5L);

        verify(skuMapper).deleteById(5L);
    }

    @Test
    void resolveForOrder_specifiedSku_shouldRequireActive() {
        PrdSku sku = sku(5L, 1L);
        sku.setStatus(PrdSkuService.STATUS_ON);
        when(skuMapper.selectById(5L)).thenReturn(sku);

        assertThat(service.resolveForOrder(1L, 5L).getId()).isEqualTo(5L);

        sku.setStatus(PrdSkuService.STATUS_OFF);
        assertThatThrownBy(() -> service.resolveForOrder(1L, 5L))
                .isInstanceOf(BizException.class)
                .hasMessage("该 SKU 已停售");
    }

    @Test
    void resolveForOrder_specifiedSkuOfOtherProduct_should400() {
        when(skuMapper.selectById(5L)).thenReturn(sku(5L, 2L));

        assertThatThrownBy(() -> service.resolveForOrder(1L, 5L))
                .isInstanceOf(BizException.class)
                .hasMessage("SKU 不存在");
    }

    @Test
    void resolveForOrder_default_shouldUseFirstActive() {
        PrdSku defaultSku = sku(5L, 1L);
        defaultSku.setStatus(PrdSkuService.STATUS_ON);
        when(skuMapper.selectOne(any(Wrapper.class))).thenReturn(defaultSku);

        assertThat(service.resolveForOrder(1L, null).getId()).isEqualTo(5L);
    }

    @Test
    void createSpecGroup_shouldInsertGroupAndValues() {
        when(productMapper.selectById(1L)).thenReturn(product(1L));
        when(groupMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(groupMapper.insert(any(PrdSpecGroup.class))).thenAnswer(inv -> {
            ((PrdSpecGroup) inv.getArgument(0)).setId(10L);
            return 1;
        });
        when(valueMapper.insert(any(PrdSpecValue.class))).thenReturn(1);

        SpecGroupCreateReq req = new SpecGroupCreateReq();
        req.setGroupName("杯型");
        req.setValues(List.of("大杯", "中杯"));
        SpecGroupVO vo = service.createSpecGroup(1L, req);

        assertThat(vo.getGroupName()).isEqualTo("杯型");
        verify(valueMapper, org.mockito.Mockito.times(2)).insert(any(PrdSpecValue.class));
    }

    @Test
    void updateSpecGroup_referencedBySku_shouldConflict() {
        when(groupMapper.selectById(10L)).thenReturn(group(10L, 1L, "杯型"));
        when(valueMapper.selectList(any(Wrapper.class))).thenReturn(List.of(value(1L, 10L, "大杯")));
        when(skuMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.updateSpecGroup(1L, 10L, new com.example.leaseplatform.prd.dto.SpecGroupUpdateReq()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已被 SKU 引用");
    }

    @Test
    void deleteSpecGroup_shouldDeleteGroupAndValues() {
        when(groupMapper.selectById(10L)).thenReturn(group(10L, 1L, "杯型"));
        when(valueMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        service.deleteSpecGroup(1L, 10L);

        verify(groupMapper).deleteById(10L);
    }

    private PrdSku sku(Long id, Long productId) {
        PrdSku s = new PrdSku();
        s.setId(id);
        s.setProductId(productId);
        s.setSkuCode("SKU000101");
        s.setPrice(1200L);
        s.setStatus(PrdSkuService.STATUS_ON);
        return s;
    }
}
