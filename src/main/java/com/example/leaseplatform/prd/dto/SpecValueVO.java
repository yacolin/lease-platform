package com.example.leaseplatform.prd.dto;

import lombok.Data;

import java.util.List;

/**
 * 规格值视图对象（1.3）。
 */
@Data
public class SpecValueVO {

    private Long id;

    private String valueName;

    private Integer sortOrder;
}
