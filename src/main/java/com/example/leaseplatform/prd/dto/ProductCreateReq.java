package com.example.leaseplatform.prd.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建商品请求。specOptions 接收 JSON 对象（如规格选项），存储时序列化为字符串。
 */
@Data
public class ProductCreateReq {

    @NotNull(message = "分类ID不能为空")
    private Long categoryId;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 100, message = "商品名称最多 100 个字符")
    private String productName;

    @NotNull(message = "商品类型不能为空")
    @Min(value = 1, message = "商品类型只能是 1-咖啡, 2-正餐, 3-加饭/加菜, 4-加汤")
    @Max(value = 4, message = "商品类型只能是 1-咖啡, 2-正餐, 3-加饭/加菜, 4-加汤")
    private Integer productType;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.0", message = "价格不能为负数")
    private BigDecimal price;

    @Size(max = 255, message = "商品描述最多 255 个字符")
    private String description;

    @Size(max = 255, message = "图片URL最多 255 个字符")
    private String imageUrl;

    /** 规格选项 JSON 对象，如 {"cup_size":["大杯","中杯"],"temperature":["热","冰"]} */
    private Object specOptions;

    private Integer isAvailable = 1;

    private Integer stock;

    private Integer sortOrder = 0;
}
