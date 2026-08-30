package com.example.leaseplatform.sys.log;

import com.example.leaseplatform.sys.entity.SysOperationLog;
import com.example.leaseplatform.sys.mapper.SysOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 操作日志切面：拦截 {@link OperationLog} 注解方法，
 * 记录操作人（后台管理员/企业管理员）、操作类型、参数摘要、IP、UA。
 * 成功/失败均记录（失败时内容追加异常信息）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final int CONTENT_MAX = 500;

    private final SysOperationLogMapper logMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterReturning(pointcut = "@annotation(operationLog)")
    public void logSuccess(JoinPoint joinPoint, OperationLog operationLog) {
        save(joinPoint, operationLog, null);
    }

    @AfterThrowing(pointcut = "@annotation(operationLog)", throwing = "e")
    public void logFailure(JoinPoint joinPoint, OperationLog operationLog, Throwable e) {
        save(joinPoint, operationLog, e);
    }

    private void save(JoinPoint joinPoint, OperationLog operationLog, Throwable e) {
        try {
            SysOperationLog entry = new SysOperationLog();
            Long operatorId = null;
            Integer operatorType = operationLog.operatorType();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.example.leaseplatform.security.LoginUser loginUser) {
                operatorId = loginUser.getId();
                if (loginUser.getUserType() != null && loginUser.getUserType() == 2) {
                    operatorType = 2; // 企业管理员
                }
            }
            entry.setOperatorId(operatorId);
            entry.setOperatorType(operatorType);
            entry.setOperationType(operationLog.value());
            entry.setOperationContent(buildContent(joinPoint, e));
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                entry.setIpAddress(clientIp(request));
                String ua = request.getHeader("User-Agent");
                entry.setUserAgent(ua == null || ua.length() <= 255 ? ua : ua.substring(0, 255));
            }
            entry.setCreatedAt(LocalDateTime.now());
            logMapper.insert(entry);
        } catch (Exception ex) {
            log.warn("操作日志写入失败", ex);
        }
    }

    /** 参数摘要 JSON（截断）+ 异常信息 */
    private String buildContent(JoinPoint joinPoint, Throwable e) {
        String params = "";
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] names = signature.getParameterNames();
            Object[] values = joinPoint.getArgs();
            if (names != null) {
                params = java.util.stream.IntStream.range(0, Math.min(names.length, values.length))
                        .mapToObj(i -> names[i] + "=" + abbreviate(String.valueOf(values[i])))
                        .collect(Collectors.joining(", "));
            } else {
                params = Arrays.stream(values).map(v -> abbreviate(String.valueOf(v)))
                        .collect(Collectors.joining(", "));
            }
        } catch (Exception ignored) {
            // 参数序列化失败不影响日志记录
        }
        if (e != null) {
            params = params + " | 失败: " + abbreviate(String.valueOf(e.getMessage()));
        }
        return abbreviate(params);
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= CONTENT_MAX ? s : s.substring(0, CONTENT_MAX) + "...";
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
