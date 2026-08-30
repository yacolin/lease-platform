package com.example.leaseplatform.ord.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 正餐预订请求（POST /api/v1/me/meal-reservations）。
 */
@Data
public class MealReservationCreateReq {

    /** 套餐商品 ID（prd_products，product_type=2） */
    @NotNull(message = "套餐商品不能为空")
    private Long productId;

    /** 菜单日期（提前 1 天、晚 8 点截止、可订未来 3 天，服务层校验） */
    @NotNull(message = "预订日期不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate menuDate;

    /** 预订时段：午餐/晚餐 */
    @NotBlank(message = "预订时段不能为空")
    @Pattern(regexp = "午餐|晚餐", message = "预订时段仅支持午餐/晚餐")
    @Schema(description = "预订时段：午餐/晚餐")
    private String timeSlot;

    /** 份数 */
    @NotNull(message = "份数不能为空")
    @Min(value = 1, message = "份数至少 1")
    @Max(value = 99, message = "单次最多 99 份")
    private Integer quantity;

    /** 配送方式：1-到店自取, 2-楼内配送, 3-周边配送 */
    @NotNull(message = "配送方式不能为空")
    @Min(value = 1, message = "配送方式：1-自取, 2-楼内, 3-周边")
    @Max(value = 3, message = "配送方式：1-自取, 2-楼内, 3-周边")
    @Schema(description = "配送方式：1-到店自取, 2-楼内配送, 3-周边配送")
    private Integer deliveryType;

    /** 配送地址（周边配送必填，服务层校验） */
    @Size(max = 255, message = "配送地址最多 255 个字符")
    private String deliveryAddress;

    @Size(max = 255, message = "备注最多 255 个字符")
    private String remark;
}
