package com.example.leaseplatform.prd.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建规格组请求（1.3，可携带规格值列表）。
 */
@Data
public class SpecGroupCreateReq {

    @NotBlank(message = "规格组名不能为空")
    @Size(max = 30, message = "规格组名最多 30 个字符")
    private String groupName;

    private Integer sortOrder = 0;

    /** 规格值列表（如 ["大杯","中杯"]） */
    private List<@NotBlank(message = "规格值不能为空") @Size(max = 30, message = "规格值最多 30 个字符") String> values;
}
