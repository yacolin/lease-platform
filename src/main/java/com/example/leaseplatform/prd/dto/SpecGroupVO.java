package com.example.leaseplatform.prd.dto;

import lombok.Data;

import java.util.List;

/**
 * 规格组视图对象（1.3，含规格值列表）。
 */
@Data
public class SpecGroupVO {

    private Long id;

    /** 商品 ID（SPU） */
    private Long productId;

    private String groupName;

    private Integer sortOrder;

    /** 规格值列表 */
    private List<SpecValueVO> values;
}
