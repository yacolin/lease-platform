package com.example.leaseplatform.prd.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 每日菜单复制请求（管理端 POST /api/v1/menus/copy）：
 * 把 sourceDate 的整单菜品复制到 targetDate（目标日期先清空再复制）。
 */
@Data
public class MenuCopyReq {

    @NotNull(message = "源日期不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate sourceDate;

    @NotNull(message = "目标日期不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate targetDate;
}
