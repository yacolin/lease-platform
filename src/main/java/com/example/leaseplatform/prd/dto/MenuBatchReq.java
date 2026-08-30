package com.example.leaseplatform.prd.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日菜单整单配置请求（管理端 POST /api/v1/menus/batch）：
 * 覆盖式配置指定日期全部菜品。
 */
@Data
public class MenuBatchReq {

    @NotNull(message = "菜单日期不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate menuDate;

    @NotEmpty(message = "至少需要一个菜品")
    @Size(max = 50, message = "单日菜品最多 50 个")
    @Valid
    private List<MenuBatchItem> items;

    @Data
    public static class MenuBatchItem {
        @NotNull(message = "套餐商品不能为空")
        private Long productId;

        @NotNull(message = "菜品名称不能为空")
        private String dishName;

        /** 菜品类型：1-荤菜, 2-素菜, 3-汤, 4-饭 */
        @NotNull(message = "菜品类型不能为空")
        private Integer dishType;

        private Integer sortOrder = 0;
    }
}
