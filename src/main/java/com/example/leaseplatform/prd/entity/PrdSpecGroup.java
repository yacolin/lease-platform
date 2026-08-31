package com.example.leaseplatform.prd.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品规格组（prd_spec_groups，1.3）：同一商品的一组规格维度（如 杯型/温度/糖度）。
 * 配置表，自增 ID。
 */
@Data
@TableName("prd_spec_groups")
public class PrdSpecGroup {

    /** 自增 ID（配置表，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品 ID（SPU） */
    private Long productId;

    /** 规格组名（如 杯型/温度/糖度） */
    private String groupName;

    /** 排序权重（升序） */
    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
