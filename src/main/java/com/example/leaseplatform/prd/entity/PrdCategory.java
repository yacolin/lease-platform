package com.example.leaseplatform.prd.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分类（prd_categories）：category_type 1-咖啡, 2-正餐。
 * 1.3 起支持二级分类（parent_id）/ 图标（icon_url）/ 展示状态（is_show）。
 */
@Data
@TableName("prd_categories")
public class PrdCategory {

    /** 自增 ID（分类配置表，量小且稳定，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String categoryName;

    /** 类型：1-咖啡, 2-正餐 */
    private Integer categoryType;

    /** 父分类 ID（0=一级分类，1.3） */
    private Long parentId;

    /** 排序权重（升序） */
    private Integer sortOrder;

    /** 状态：0-禁用, 1-启用 */
    private Integer status;

    /** 分类图标 URL（1.3） */
    private String iconUrl;

    /** 是否展示：0-不展示, 1-展示（1.3） */
    private Integer isShow;

    /** 逻辑删除：0-未删除, 1-已删除 */
    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
