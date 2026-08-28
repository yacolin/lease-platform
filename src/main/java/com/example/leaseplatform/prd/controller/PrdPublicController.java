package com.example.leaseplatform.prd.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.prd.dto.CategoryVO;
import com.example.leaseplatform.prd.dto.MenuVO;
import com.example.leaseplatform.prd.dto.ProductVO;
import com.example.leaseplatform.prd.service.PrdCategoryService;
import com.example.leaseplatform.prd.service.PrdDailyMenuService;
import com.example.leaseplatform.prd.service.PrdProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 小程序公开接口（/api/v1/wx/**，白名单放行，无需登录）：
 * 商品分类 / 商品浏览 / 每日菜单。
 */
@RestController
@RequestMapping("/api/v1/wx")
@RequiredArgsConstructor
public class PrdPublicController {

    private final PrdCategoryService categoryService;
    private final PrdProductService productService;
    private final PrdDailyMenuService menuService;

    /** 商品分类列表（仅启用） */
    @GetMapping("/categories")
    public ApiResponse<List<CategoryVO>> categories() {
        return ApiResponse.ok(categoryService.publicList());
    }

    /** 商品分页列表（仅上架；可按分类/类型筛选） */
    @GetMapping("/products")
    public ApiResponse<PageResult<ProductVO>> products(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer productType) {
        return ApiResponse.ok(productService.publicPage(page, size, categoryId, productType));
    }

    /** 商品详情（仅上架） */
    @GetMapping("/products/{id}")
    public ApiResponse<ProductVO> product(@PathVariable Long id) {
        return ApiResponse.ok(productService.publicGet(id));
    }

    /** 每日菜单（按日期查询，缺省今天） */
    @GetMapping("/menus")
    public ApiResponse<List<MenuVO>> menus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(menuService.publicList(date));
    }
}
