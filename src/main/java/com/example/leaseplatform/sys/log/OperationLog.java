package com.example.leaseplatform.sys.log;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在需要记录后台操作的方法上（管理端审核/CRUD 等），
 * 由 {@link OperationLogAspect} 切面统一记录到 sys_operation_logs。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 操作类型描述（如"审核企业"、"上架商品"） */
    String value();

    /** 操作人类型：1-系统管理员, 2-企业管理员（缺省 1） */
    int operatorType() default 1;
}
