package com.example.leaseplatform.prd.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品视图对象（含分类名称；specOptions 解析为 JSON 对象返回）。
 */
@Data
public class ProductVO {

    private Long id;

    private Long categoryId;

    /** 分类名称（冗余展示） */
    private String categoryName;

    private String productName;

    private Integer productType;

    private BigDecimal price;

    private String description;

    private String imageUrl;

    /** 规格选项 JSON 对象 */
    private Object specOptions;

    private Integer isAvailable;

    private Integer stock;

    private Integer sortOrder;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
