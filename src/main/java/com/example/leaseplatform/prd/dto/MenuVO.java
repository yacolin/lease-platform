package com.example.leaseplatform.prd.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    private Integer dishType;

    private Integer sortOrder;

    private Integer isAvailable;

    /** 创建时间（epoch 毫秒时间戳） */
    private Long createdAt;

    /** 更新时间（epoch 毫秒时间戳） */
    private Long updatedAt;
}
