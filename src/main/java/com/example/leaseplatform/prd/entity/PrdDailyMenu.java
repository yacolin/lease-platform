package com.example.leaseplatform.prd.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日菜单（prd_daily_menus）：正餐每日菜品。
 * dish_type 1-荤菜, 2-素菜, 3-汤, 4-饭。
 * 注意：1.0 设计该表无 is_deleted 字段（按日生成、物理删除即可）。
 */
@Data
@TableName("prd_daily_menus")
public class PrdDailyMenu {

    /** 雪花 ID（每日菜单按天自动生成、数据持续积累，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 菜单日期 */
    private LocalDate menuDate;

    /** 关联套餐商品ID（prd_products.id） */
    private Long productId;

    /** 菜品名称 */
    private String dishName;

    /** 菜品类型：1-荤菜, 2-素菜, 3-汤, 4-饭 */
    private Integer dishType;

    /** 排序权重（升序） */
    private Integer sortOrder;

    /** 是否供应：0-停售, 1-供应 */
    private Integer isAvailable;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
