package com.example.leaseplatform.prd.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日菜单视图对象（含套餐名称）。
 */
@Data
public class MenuVO {

    private Long id;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate menuDate;

    private Long productId;

    /** 套餐名称（冗余展示） */
    private String productName;

    private String dishName;

    private Integer dishType;

    private Integer sortOrder;

    private Integer isAvailable;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
