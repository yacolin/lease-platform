package com.example.leaseplatform.prd;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * prd 域端到端集成测试（真实 MySQL + 完整 Security 链）。
 * 依赖：先执行 ./reset_db.sh 建库并写入种子数据（db/02_prd.sql），
 * 断言基于仓库内固定的种子数据（4 分类 / 8 商品 / 2026-08-30 菜单 11 条）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PrdPublicApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicCategories_shouldReturnSeededCategories() throws Exception {
        mockMvc.perform(get("/api/v1/public/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].categoryName").value("咖啡"))
                .andExpect(jsonPath("$.data[1].categoryName").value("正餐"))
                .andExpect(jsonPath("$.data[2].categoryName").value("加餐/加菜"))
                .andExpect(jsonPath("$.data[3].categoryName").value("加汤"));
    }

    @Test
    void publicProducts_shouldReturnSeededProductsWithSpec() throws Exception {
        mockMvc.perform(get("/api/v1/public/products").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(8))
                .andExpect(jsonPath("$.data.list[0].productName").value("美式"))
                .andExpect(jsonPath("$.data.list[0].categoryName").value("咖啡"))
                .andExpect(jsonPath("$.data.list[0].specOptions.cup_size[0]").value("大杯"))
                // 时间字段为数字时间戳（epoch 毫秒），非字符串
                .andExpect(jsonPath("$.data.list[0].createdAt").isNumber());
    }

    @Test
    void publicProducts_shouldFilterByCategoryAndType() throws Exception {
        mockMvc.perform(get("/api/v1/public/products")
                        .param("categoryId", "2")
                        .param("productType", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void publicProductDetail_shouldReturnSeededProduct() throws Exception {
        mockMvc.perform(get("/api/v1/public/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("美式"))
                .andExpect(jsonPath("$.data.price").value(1200))
                .andExpect(jsonPath("$.data.specOptions.temperature[1]").value("冰"));
    }

    @Test
    void publicMenus_shouldReturnSeededMenusWithProductName() throws Exception {
        mockMvc.perform(get("/api/v1/public/menus").param("date", "2026-08-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(11))
                .andExpect(jsonPath("$.data[0].dishName").value("红烧肉"))
                .andExpect(jsonPath("$.data[0].productName").value("3荤1素套餐"))
                // 日期字段保持字符串、时间字段为数字时间戳
                .andExpect(jsonPath("$.data[0].menuDate").value("2026-08-30"))
                .andExpect(jsonPath("$.data[0].createdAt").isNumber());
    }

    @Test
    void adminEndpoints_withoutAuth_shouldBeForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"x\",\"categoryType\":1}"))
                .andExpect(status().isForbidden());
    }
}
