package com.example.leaseplatform.common;

import lombok.extern.slf4j.Slf4j;
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
 * 全局异常处理（对应 campus_express pkg/middleware/errorhandler + response.BindError）：
 * - 业务异常 BizException：按携带的 HTTP 状态返回 {code, message}
 * - 参数校验失败：422 + 字段级错误明细 {field, message}
 * - 其余异常：500，对外不暴露细节
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 字段校验错误明细（对应 campus_express 的 ValidationFieldError） */
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
