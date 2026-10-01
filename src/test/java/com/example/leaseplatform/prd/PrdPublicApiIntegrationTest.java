package com.example.leaseplatform.prd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.example.leaseplatform.support.CacheTestSupport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * 依赖：先执行 make db-reset（建表 + db/seed.py 灌种子），
 * 断言基于 seed.py 的核心种子（4 分类 / 27 商品（含 8 个核心）/ 2026-08-30 菜单 11 条）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PrdPublicApiIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void flushCacheNamespace() {
        CacheTestSupport.flushCacheNamespace(redisTemplate);
    }

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
                .andExpect(jsonPath("$.data.total").value(27))
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
                .andExpect(jsonPath("$.data.total").value(6));
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
    void publicProductDetail_shouldReturnSkusAndSpecGroups() throws Exception {
        // 1.3：种子为有规格商品生成 规格组/规格值 + SKU 笛卡尔积（美式 2杯型×2温度×3糖度=12 SKU）
        mockMvc.perform(get("/api/v1/public/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skus.length()").value(12))
                .andExpect(jsonPath("$.data.skus[0].skuCode").value("SKU000101"))
                .andExpect(jsonPath("$.data.skus[0].price").value(1200))
                .andExpect(jsonPath("$.data.skus[0].specSnapshot.cup_size").value("大杯"))
                .andExpect(jsonPath("$.data.specGroups.length()").value(3))
                .andExpect(jsonPath("$.data.specGroups[0].groupName").value("cup_size"))
                .andExpect(jsonPath("$.data.specGroups[0].values.length()").value(2));
        // 无规格商品（如加饭 id=6）→ 1 条默认 SKU（specSnapshot 为 null，JSON non_null 省略）
        mockMvc.perform(get("/api/v1/public/products/6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skus.length()").value(1))
                .andExpect(jsonPath("$.data.skus[0].skuCode").value("SKU000600"))
                .andExpect(jsonPath("$.data.skus[0].specSnapshot").doesNotExist());
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
