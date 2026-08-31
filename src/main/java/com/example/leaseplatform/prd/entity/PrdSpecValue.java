package com.example.leaseplatform.prd.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品规格值（prd_spec_values，1.3）：规格组下的可选值（如 大杯/热/无糖）。
 * 配置表，自增 ID。
 */
@Data
@TableName("prd_spec_values")
public class PrdSpecValue {

    /** 自增 ID（配置表，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规格组 ID（prd_spec_groups.id） */
    private Long groupId;

    /** 规格值名（如 大杯/热/无糖） */
    private String valueName;

    /** 排序权重（升序） */
    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
