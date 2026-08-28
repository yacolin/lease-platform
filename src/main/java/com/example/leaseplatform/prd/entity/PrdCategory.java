package com.example.leaseplatform.prd.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分类（prd_categories）：category_type 1-咖啡, 2-正餐。
 */
@Data
@TableName("prd_categories")
public class PrdCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String categoryName;

    /** 类型：1-咖啡, 2-正餐 */
    private Integer categoryType;

    /** 排序权重（升序） */
    private Integer sortOrder;

    /** 状态：0-禁用, 1-启用 */
    private Integer status;

    /** 逻辑删除：0-未删除, 1-已删除 */
    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
