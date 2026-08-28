package com.example.leaseplatform.prd.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.prd.dto.CategoryVO;
import com.example.leaseplatform.prd.dto.ProductVO;
import com.example.leaseplatform.prd.service.PrdCategoryService;
import com.example.leaseplatform.prd.service.PrdDailyMenuService;
import com.example.leaseplatform.prd.service.PrdProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商家后台管理接口 Web 层测试。
 * 管理端路径不在白名单内，通过 @WithMockUser 走真实 Security 链（authenticated）。
 */
@WebMvcTest(PrdAdminController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@WithMockUser
class PrdAdminControllerTest {

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
        return vo;
    }

    @Test
    void createCategory_shouldReturnVo() throws Exception {
        when(categoryService.create(any())).thenReturn(categoryVO());

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"咖啡\",\"categoryType\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.categoryName").value("咖啡"));
    }

    @Test
    void createCategory_invalidBody_shouldReturn422WithFieldErrors() throws Exception {
        // 字段错误列表顺序不保证确定，断言用集合匹配而非下标
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryType\":9}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("参数校验失败"))
                .andExpect(jsonPath("$.data[*].field", hasItems("categoryName", "categoryType")))
                .andExpect(jsonPath("$.data[*].message", hasItem("分类名称不能为空")));
    }

    @Test
    void listCategories_shouldReturnPagedResult() throws Exception {
        when(categoryService.page(1, 10, null, null))
                .thenReturn(PageResult.of(1, List.of(categoryVO())));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void getCategory_shouldReturnVo() throws Exception {
        when(categoryService.getById(1L)).thenReturn(categoryVO());

        mockMvc.perform(get("/api/v1/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("咖啡"));
    }

    @Test
    void updateCategory_shouldReturnVo() throws Exception {
        when(categoryService.update(eq(1L), any())).thenReturn(categoryVO());

        mockMvc.perform(put("/api/v1/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"正餐\",\"categoryType\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("咖啡"));
    }

    @Test
    void deleteCategory_shouldSucceed() throws Exception {
        doNothing().when(categoryService).delete(1L);

        mockMvc.perform(delete("/api/v1/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(categoryService).delete(1L);
    }

    @Test
    void createProduct_shouldReturnVo() throws Exception {
        ProductVO vo = new ProductVO();
        vo.setId(1L);
        vo.setProductName("美式");
        vo.setPrice(new BigDecimal("12.00"));
        when(productService.create(any())).thenReturn(vo);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":1,"productName":"美式","productType":1,"price":12.00,
                                 "specOptions":{"cup_size":["大杯","中杯"]}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("美式"));
    }

    @Test
    void updateProductStatus_shouldReturnVo() throws Exception {
        ProductVO vo = new ProductVO();
        vo.setId(1L);
        vo.setIsAvailable(0);
        when(productService.updateStatus(eq(1L), any())).thenReturn(vo);

        mockMvc.perform(put("/api/v1/products/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAvailable\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isAvailable").value(0));
    }

    @Test
    void deleteProduct_shouldSucceed() throws Exception {
        doNothing().when(productService).delete(1L);

        mockMvc.perform(delete("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void createMenu_shouldReturnVo() throws Exception {
        com.example.leaseplatform.prd.dto.MenuVO vo = new com.example.leaseplatform.prd.dto.MenuVO();
        vo.setId(1L);
        vo.setDishName("红烧肉");
        when(menuService.create(any())).thenReturn(vo);

        mockMvc.perform(post("/api/v1/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"menuDate":"2026-08-30","productId":4,"dishName":"红烧肉","dishType":1}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dishName").value("红烧肉"));
    }
}
