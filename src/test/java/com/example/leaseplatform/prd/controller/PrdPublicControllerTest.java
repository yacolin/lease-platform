package com.example.leaseplatform.prd.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.prd.dto.CategoryVO;
import com.example.leaseplatform.prd.dto.MenuVO;
import com.example.leaseplatform.prd.dto.ProductVO;
import com.example.leaseplatform.prd.service.PrdCategoryService;
import com.example.leaseplatform.prd.service.PrdDailyMenuService;
import com.example.leaseplatform.prd.service.PrdProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公开接口 Web 层测试（走真实 Security 白名单 /api/v1/public/**）。
 */
@WebMvcTest(PrdPublicController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class PrdPublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PrdCategoryService categoryService;
    @MockitoBean
    private PrdProductService productService;
    @MockitoBean
    private PrdDailyMenuService menuService;

    private CategoryVO categoryVO() {
        CategoryVO vo = new CategoryVO();
        vo.setId(1L);
        vo.setCategoryName("咖啡");
        vo.setCategoryType(1);
        vo.setSortOrder(1);
        vo.setStatus(1);
        return vo;
    }

    private ProductVO productVO() {
        ProductVO vo = new ProductVO();
        vo.setId(1L);
        vo.setCategoryId(1L);
        vo.setCategoryName("咖啡");
        vo.setProductName("美式");
        vo.setPrice(new BigDecimal("12.00"));
        vo.setIsAvailable(1);
        return vo;
    }

    @Test
    void categories_shouldReturnEnvelopeList() throws Exception {
        when(categoryService.publicList()).thenReturn(List.of(categoryVO()));

        mockMvc.perform(get("/api/v1/public/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data[0].categoryName").value("咖啡"));
    }

    @Test
    void products_shouldReturnPagedResult() throws Exception {
        when(productService.publicPage(1, 10, null, null))
                .thenReturn(PageResult.of(1, List.of(productVO())));

        mockMvc.perform(get("/api/v1/public/products").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].productName").value("美式"));
    }

    @Test
    void productDetail_shouldReturnVo() throws Exception {
        when(productService.publicGet(1L)).thenReturn(productVO());

        mockMvc.perform(get("/api/v1/public/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("美式"));
    }

    @Test
    void menus_shouldReturnList() throws Exception {
        MenuVO vo = new MenuVO();
        vo.setId(1L);
        vo.setMenuDate(LocalDate.of(2026, 8, 30));
        vo.setDishName("红烧肉");
        vo.setProductName("3荤1素套餐");
        when(menuService.publicList(eq(LocalDate.of(2026, 8, 30)))).thenReturn(List.of(vo));

        mockMvc.perform(get("/api/v1/public/menus").param("date", "2026-08-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].dishName").value("红烧肉"))
                .andExpect(jsonPath("$.data[0].menuDate").value("2026-08-30"));
    }

    @Test
    void unknownProduct_shouldReturn404WithErrorCode() throws Exception {
        when(productService.publicGet(any())).thenThrow(new com.example.leaseplatform.common.BizException(
                com.example.leaseplatform.common.ErrorCode.NOT_FOUND, "商品不存在",
                org.springframework.http.HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/v1/public/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("商品不存在"));
    }
}
