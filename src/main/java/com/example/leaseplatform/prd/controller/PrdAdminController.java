package com.example.leaseplatform.prd.controller;

import com.example.leaseplatform.sys.log.OperationLog;
import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.prd.dto.CategoryCreateReq;
import com.example.leaseplatform.prd.dto.CategoryUpdateReq;
import com.example.leaseplatform.prd.dto.CategoryVO;
import com.example.leaseplatform.prd.dto.MenuCreateReq;
import com.example.leaseplatform.prd.dto.MenuUpdateReq;
import com.example.leaseplatform.prd.dto.MenuVO;
import com.example.leaseplatform.prd.dto.ProductCreateReq;
import com.example.leaseplatform.prd.dto.ProductStatusReq;
import com.example.leaseplatform.prd.dto.ProductUpdateReq;
import com.example.leaseplatform.prd.dto.ProductVO;
import com.example.leaseplatform.prd.dto.SkuCreateReq;
import com.example.leaseplatform.prd.dto.SkuStatusReq;
import com.example.leaseplatform.prd.dto.SkuUpdateReq;
import com.example.leaseplatform.prd.dto.SkuVO;
import com.example.leaseplatform.prd.dto.SpecGroupCreateReq;
import com.example.leaseplatform.prd.dto.SpecGroupUpdateReq;
import com.example.leaseplatform.prd.dto.SpecGroupVO;
import com.example.leaseplatform.prd.service.PrdCategoryService;
import com.example.leaseplatform.prd.service.PrdDailyMenuService;
import com.example.leaseplatform.prd.service.PrdProductService;
import com.example.leaseplatform.prd.service.PrdSkuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 商家后台商品管理接口（/api/v1/**，需登录；JWT 认证接入后生效）：
 * 分类 / 商品（SPU）/ SKU / 规格组 / 每日菜单的完整 CRUD（1.3 商品中心 SKU 化）。
 */
@Tag(name = "productAdmin", description = "商品管理（管理端）：需登录，JWT 认证接入后生效")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PrdAdminController {

    private final PrdCategoryService categoryService;
    private final PrdProductService productService;
    private final PrdSkuService skuService;
    private final PrdDailyMenuService menuService;

    // ==================== 商品分类 ====================

    @Operation(operationId = "createCategory", summary = "创建分类")
    @PostMapping("/categories")
    public ApiResponse<CategoryVO> createCategory(@Valid @RequestBody CategoryCreateReq req) {
        return ApiResponse.ok(categoryService.create(req));
    }

    @Operation(operationId = "listCategories", summary = "分类分页列表")
    @GetMapping("/categories")
    public ApiResponse<PageResult<CategoryVO>> listCategories(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer categoryType,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(categoryService.page(page, size, categoryType, status));
    }

    @Operation(operationId = "getCategory", summary = "分类详情")
    @GetMapping("/categories/{id}")
    public ApiResponse<CategoryVO> getCategory(@PathVariable Long id) {
        return ApiResponse.ok(categoryService.getById(id));
    }

    @Operation(operationId = "updateCategory", summary = "更新分类")
    @PutMapping("/categories/{id}")
    public ApiResponse<CategoryVO> updateCategory(@PathVariable Long id,
                                                  @Valid @RequestBody CategoryUpdateReq req) {
        return ApiResponse.ok(categoryService.update(id, req));
    }

    @Operation(operationId = "deleteCategory", summary = "删除分类")
    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.ok(null);
    }

    // ==================== 商品 ====================

    @Operation(operationId = "createProduct", summary = "创建商品")
    @PostMapping("/products")
    public ApiResponse<ProductVO> createProduct(@Valid @RequestBody ProductCreateReq req) {
        return ApiResponse.ok(productService.create(req));
    }

    @Operation(operationId = "listProducts", summary = "商品分页列表")
    @GetMapping("/products")
    public ApiResponse<PageResult<ProductVO>> listProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer productType,
            @RequestParam(required = false) Integer isAvailable,
            @RequestParam(required = false) Integer productStatus,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(productService.page(page, size, categoryId, productType,
                isAvailable, productStatus, keyword));
    }

    @Operation(operationId = "getProduct", summary = "商品详情")
    @GetMapping("/products/{id}")
    public ApiResponse<ProductVO> getProduct(@PathVariable Long id) {
        return ApiResponse.ok(productService.getById(id));
    }

    @Operation(operationId = "updateProduct", summary = "更新商品")
    @PutMapping("/products/{id}")
    public ApiResponse<ProductVO> updateProduct(@PathVariable Long id,
                                                @Valid @RequestBody ProductUpdateReq req) {
        return ApiResponse.ok(productService.update(id, req));
    }

    /** 上下架（状态推进风格） */
    @Operation(operationId = "updateProductStatus", summary = "商品上下架/状态生命周期（0-草稿, 1-待审核, 2-上架, 3-下架, 4-停售）")

    @OperationLog("商品上下架")
    @PutMapping("/products/{id}/status")
    public ApiResponse<ProductVO> updateProductStatus(@PathVariable Long id,
                                                      @Valid @RequestBody ProductStatusReq req) {
        return ApiResponse.ok(productService.updateStatus(id, req));
    }

    @Operation(operationId = "deleteProduct", summary = "删除商品")
    @DeleteMapping("/products/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.ok(null);
    }

    // ==================== SKU（1.3 商品中心 SKU 化） ====================

    @Operation(operationId = "listProductSkus", summary = "商品 SKU 列表")
    @GetMapping("/products/{id}/skus")
    public ApiResponse<List<SkuVO>> listSkus(@PathVariable Long id) {
        return ApiResponse.ok(skuService.listByProduct(id));
    }

    @Operation(operationId = "createProductSku", summary = "创建 SKU（specValueIds 为空 → 默认 SKU）")
    @OperationLog("创建SKU")
    @PostMapping("/products/{id}/skus")
    public ApiResponse<SkuVO> createSku(@PathVariable Long id,
                                        @Valid @RequestBody SkuCreateReq req) {
        return ApiResponse.ok(skuService.create(id, req));
    }

    @Operation(operationId = "updateProductSku", summary = "更新 SKU")
    @PutMapping("/products/{id}/skus/{skuId}")
    public ApiResponse<SkuVO> updateSku(@PathVariable Long id,
                                        @PathVariable Long skuId,
                                        @Valid @RequestBody SkuUpdateReq req) {
        return ApiResponse.ok(skuService.update(id, skuId, req));
    }

    @Operation(operationId = "deleteProductSku", summary = "删除 SKU（被订单引用时拒绝）")
    @DeleteMapping("/products/{id}/skus/{skuId}")
    public ApiResponse<Void> deleteSku(@PathVariable Long id, @PathVariable Long skuId) {
        skuService.delete(id, skuId);
        return ApiResponse.ok(null);
    }

    @Operation(operationId = "updateProductSkuStatus", summary = "SKU 上下架（0-停售, 1-可售）")
    @PutMapping("/products/{id}/skus/{skuId}/status")
    public ApiResponse<SkuVO> updateSkuStatus(@PathVariable Long id,
                                              @PathVariable Long skuId,
                                              @Valid @RequestBody SkuStatusReq req) {
        return ApiResponse.ok(skuService.updateStatus(id, skuId, req.getStatus()));
    }

    // ==================== 规格组 / 规格值（1.3） ====================

    @Operation(operationId = "listProductSpecGroups", summary = "商品规格组列表（含规格值）")
    @GetMapping("/products/{id}/spec-groups")
    public ApiResponse<List<SpecGroupVO>> listSpecGroups(@PathVariable Long id) {
        return ApiResponse.ok(skuService.listSpecGroups(id));
    }

    @Operation(operationId = "createProductSpecGroup", summary = "创建规格组（可携带规格值）")
    @OperationLog("创建规格组")
    @PostMapping("/products/{id}/spec-groups")
    public ApiResponse<SpecGroupVO> createSpecGroup(@PathVariable Long id,
                                                    @Valid @RequestBody SpecGroupCreateReq req) {
        return ApiResponse.ok(skuService.createSpecGroup(id, req));
    }

    @Operation(operationId = "updateProductSpecGroup", summary = "更新规格组（values 全量替换；已被 SKU 引用时拒绝）")
    @PutMapping("/products/{id}/spec-groups/{groupId}")
    public ApiResponse<SpecGroupVO> updateSpecGroup(@PathVariable Long id,
                                                    @PathVariable Long groupId,
                                                    @Valid @RequestBody SpecGroupUpdateReq req) {
        return ApiResponse.ok(skuService.updateSpecGroup(id, groupId, req));
    }

    @Operation(operationId = "deleteProductSpecGroup", summary = "删除规格组（连同规格值；已被 SKU 引用时拒绝）")
    @DeleteMapping("/products/{id}/spec-groups/{groupId}")
    public ApiResponse<Void> deleteSpecGroup(@PathVariable Long id, @PathVariable Long groupId) {
        skuService.deleteSpecGroup(id, groupId);
        return ApiResponse.ok(null);
    }

    // ==================== 每日菜单 ====================

    @Operation(operationId = "createMenu", summary = "创建菜单项")
    @PostMapping("/menus")
    public ApiResponse<MenuVO> createMenu(@Valid @RequestBody MenuCreateReq req) {
        return ApiResponse.ok(menuService.create(req));
    }

    @Operation(operationId = "listMenus", summary = "菜单分页列表")
    @GetMapping("/menus")
    public ApiResponse<PageResult<MenuVO>> listMenus(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long productId) {
        return ApiResponse.ok(menuService.page(page, size, date, productId));
    }

    @Operation(operationId = "getMenu", summary = "菜单项详情")
    @GetMapping("/menus/{id}")
    public ApiResponse<MenuVO> getMenu(@PathVariable Long id) {
        return ApiResponse.ok(menuService.getById(id));
    }

    @Operation(operationId = "updateMenu", summary = "更新菜单项")
    @PutMapping("/menus/{id}")
    public ApiResponse<MenuVO> updateMenu(@PathVariable Long id,
                                          @Valid @RequestBody MenuUpdateReq req) {
        return ApiResponse.ok(menuService.update(id, req));
    }

    @Operation(operationId = "deleteMenu", summary = "删除菜单项")
    @DeleteMapping("/menus/{id}")
    public ApiResponse<Void> deleteMenu(@PathVariable Long id) {
        menuService.delete(id);
        return ApiResponse.ok(null);
    }

    @Operation(operationId = "batchMenus", summary = "整单配置菜单（覆盖指定日期全部菜品）")
    @PostMapping("/menus/batch")
    public ApiResponse<java.util.List<MenuVO>> batchMenus(
            @Valid @RequestBody com.example.leaseplatform.prd.dto.MenuBatchReq req) {
        return ApiResponse.ok(menuService.batchCreate(req));
    }

    @Operation(operationId = "copyMenus", summary = "复制整单菜单（sourceDate → targetDate，目标日期先清空）")
    @PostMapping("/menus/copy")
    public ApiResponse<Void> copyMenus(
            @Valid @RequestBody com.example.leaseplatform.prd.dto.MenuCopyReq req) {
        menuService.copy(req);
        return ApiResponse.ok(null);
    }

    @Operation(operationId = "clearMenusByDate", summary = "按日期清空菜单")
    @DeleteMapping("/menus")
    public ApiResponse<Void> deleteMenusByDate(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date) {
        menuService.deleteByDate(date);
        return ApiResponse.ok(null);
    }
}
