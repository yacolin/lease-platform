package com.example.leaseplatform.prd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新规格组请求（1.3，values 全量替换；已被 SKU 引用的规格值不可变更）。
 */
@Data
public class SpecGroupUpdateReq {

    @NotBlank(message = "规格组名不能为空")
    @Size(max = 30, message = "规格组名最多 30 个字符")
    private String groupName;

    private Integer sortOrder;

    /** 规格值列表（全量替换） */
    private List<@NotBlank(message = "规格值不能为空") @Size(max = 30, message = "规格值最多 30 个字符") String> values;
}
