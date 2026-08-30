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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PrdProductServiceTest {

    @Mock
    private PrdProductMapper productMapper;
    @Mock
    private PrdCategoryMapper categoryMapper;
    @Mock
    private PrdDailyMenuMapper menuMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PrdProductService service;

    @BeforeEach
    void setUp() {
        service = new PrdProductService(productMapper, categoryMapper, menuMapper, objectMapper);
    }

    private PrdCategory category(Long id) {
        PrdCategory c = new PrdCategory();
        c.setId(id);
        c.setCategoryName(id == 1L ? "咖啡" : "正餐");
        return c;
    }

    private PrdProduct product(Long id, Long categoryId, String name, int available) {
        PrdProduct p = new PrdProduct();
        p.setId(id);
        p.setCategoryId(categoryId);
        p.setProductName(name);
        p.setProductType(1);
        p.setPrice(1200L);
        p.setIsAvailable(available);
        p.setSpecOptions("{\"cup_size\":[\"大杯\",\"中杯\"]}");
        return p;
    }

    private ProductCreateReq createReq() {
        ProductCreateReq req = new ProductCreateReq();
        req.setCategoryId(1L);
        req.setProductName("美式");
        req.setProductType(1);
        req.setPrice(1200L);
        req.setSpecOptions(Map.of("cup_size", List.of("大杯", "中杯")));
        return req;
    }

    @Test
    void create_shouldSerializeSpecOptionsAndInsert() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L));
        when(productMapper.insert(any(PrdProduct.class))).thenAnswer(inv -> {
            PrdProduct p = inv.getArgument(0);
            p.setId(1L);
            return 1;
        });

        ProductVO vo = service.create(createReq());

        ArgumentCaptor<PrdProduct> captor = ArgumentCaptor.forClass(PrdProduct.class);
        verify(productMapper).insert(captor.capture());
        // 规格对象被序列化为 JSON 字符串入库
        JsonNode stored = objectMapper.readTree(captor.getValue().getSpecOptions());
        assertThat(stored.get("cup_size").get(0).asText()).isEqualTo("大杯");
        // VO 中解析回对象
        JsonNode returned = (JsonNode) vo.getSpecOptions();
        assertThat(returned.get("cup_size").get(1).asText()).isEqualTo("中杯");
    }

    @Test
    void create_missingCategory_shouldThrow404() {
        when(categoryMapper.selectById(99L)).thenReturn(null);
        ProductCreateReq req = createReq();
        req.setCategoryId(99L);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BizException.class)
                .hasMessage("分类不存在");
        verify(productMapper, never()).insert(any(PrdProduct.class));
    }

    @Test
    void update_shouldPersist() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L));
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1L, "美式", 1));
        ProductUpdateReq req = new ProductUpdateReq();
        req.setCategoryId(1L);
        req.setProductName("拿铁");
        req.setProductType(1);
        req.setPrice(1500L);

        ProductVO vo = service.update(1L, req);

        assertThat(vo.getProductName()).isEqualTo("拿铁");
        verify(productMapper).updateById(any(PrdProduct.class));
    }

    @Test
    void update_missing_shouldThrow404() {
        when(productMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(1L, new ProductUpdateReq()))
                .isInstanceOf(BizException.class)
                .hasMessage("商品不存在");
    }

    @Test
    void updateStatus_shouldSetAvailable() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1L, "美式", 1));
        ProductStatusReq req = new ProductStatusReq();
        req.setIsAvailable(0);

        ProductVO vo = service.updateStatus(1L, req);

        ArgumentCaptor<PrdProduct> captor = ArgumentCaptor.forClass(PrdProduct.class);
        verify(productMapper).updateById(captor.capture());
        assertThat(captor.getValue().getIsAvailable()).isZero();
        assertThat(vo.getIsAvailable()).isZero();
    }

    @Test
    void delete_referencedByMenu_shouldConflict() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1L, "美式", 1));
        when(menuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法删除");
        verify(productMapper, never()).deleteById(any(PrdProduct.class));
    }

    @Test
    void delete_withoutReference_shouldDelete() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1L, "美式", 1));
        when(menuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.delete(1L);

        verify(productMapper).deleteById(1L);
    }

    @Test
    void getById_shouldFillCategoryName() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1L, "美式", 1));
        when(categoryMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(category(1L)));

        ProductVO vo = service.getById(1L);

        assertThat(vo.getCategoryName()).isEqualTo("咖啡");
    }

    @Test
    void publicGet_available_shouldReturn() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1L, "美式", 1));

        ProductVO vo = service.publicGet(1L);

        assertThat(vo.getProductName()).isEqualTo("美式");
        assertThat(vo.getSpecOptions()).isInstanceOf(JsonNode.class);
    }

    @Test
    void publicGet_unavailable_shouldThrow404() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1L, "美式", 0));

        assertThatThrownBy(() -> service.publicGet(1L))
                .isInstanceOf(BizException.class)
                .hasMessage("商品不存在");
    }

    @Test
    void publicPage_shouldReturnTotalAndList() {
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            Page<PrdProduct> p = inv.getArgument(0);
            p.setRecords(List.of(product(1L, 1L, "美式", 1)));
            p.setTotal(1);
            return p;
        });

        PageResult<ProductVO> result = service.publicPage(1, 10, null, null);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list().get(0).getProductName()).isEqualTo("美式");
    }
}
