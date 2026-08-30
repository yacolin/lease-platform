package com.example.leaseplatform.prd.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 每日菜单视图对象（含套餐名称）。
 */
@Data
public class MenuVO {

    private Long id;

    /** 菜单日期（日期非时刻，保持 yyyy-MM-dd，避免时区歧义） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate menuDate;

    private Long productId;

    /** 套餐名称（冗余展示） */
    private String productName;

    private String dishName;

    /** 菜品类型：1-荤菜, 2-素菜, 3-汤, 4-饭（注意与商品类型 product_type 的枚举不同，勿混淆） */
    @Schema(description = "菜品类型：1-荤菜, 2-素菜, 3-汤, 4-饭")
    private Integer dishType;

    private Integer sortOrder;

    private Integer isAvailable;

    /** 创建时间（epoch 毫秒时间戳） */
    private Long createdAt;

    /** 更新时间（epoch 毫秒时间戳） */
    private Long updatedAt;
}
