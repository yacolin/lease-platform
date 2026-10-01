package com.example.leaseplatform.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;
import java.util.List;

/**
 * 全局异常处理：
 * - 业务异常 BizException：按携带的 HTTP 状态返回 {code, message}
 * - 参数校验失败：422 + 字段级错误明细 {field, message}
 * - 其余异常：500，对外不暴露细节
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 字段校验错误明细 */
    public record FieldErrorVO(String field, String message) {
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    /** @Valid 请求体校验失败（422） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorVO>>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        return buildValidationError(e.getBindingResult().getFieldErrors());
    }

    /** 表单绑定校验失败（422） */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorVO>>> handleBindException(BindException e) {
        return buildValidationError(e.getBindingResult().getFieldErrors());
    }

    /** 方法参数（@RequestParam/@PathVariable）约束校验失败（422） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorVO>>> handleConstraintViolation(ConstraintViolationException e) {
        List<FieldErrorVO> errors = e.getConstraintViolations().stream()
                .map(v -> new FieldErrorVO(
                        v.getPropertyPath() == null ? "" : v.getPropertyPath().toString(),
                        v.getMessage()))
                .toList();
        ApiResponse<List<FieldErrorVO>> body = ApiResponse.error(ErrorCode.INVALID_PARAMS, "参数校验失败");
        body.setData(errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    /**
     * 唯一键冲突（409）。
     *
     * <p>多处「先 selectCount 查重再 insert」的写法，其查重只是<b>启发式</b>：
     * 并发下两个事务都可能通过预检，最终由唯一索引拦下其中一个（TOCTOU 竞态）。
     * 这里把唯一索引抛出的冲突兜底为 409，而不是落入 {@link #handleException} 变成 500。
     *
     * <p>只处理 {@link DuplicateKeyException}（MySQL 1062），不扩大到父类
     * {@code DataIntegrityViolationException}，以免把外键/非空等其它完整性问题误报为冲突。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一键冲突（并发写入同一唯一值）: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.CONFLICT, "数据已存在或与现有记录冲突"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("系统错误", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "服务器内部错误"));
    }

    private ResponseEntity<ApiResponse<List<FieldErrorVO>>> buildValidationError(List<FieldError> fieldErrors) {
        List<FieldErrorVO> details = fieldErrors.stream()
                .map(fe -> new FieldErrorVO(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiResponse<List<FieldErrorVO>> body = ApiResponse.error(ErrorCode.INVALID_PARAMS, "参数校验失败");
        body.setData(details);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }
}
