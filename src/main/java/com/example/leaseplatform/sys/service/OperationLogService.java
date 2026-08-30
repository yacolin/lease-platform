package com.example.leaseplatform.sys.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.sys.dto.OperationLogVO;
import com.example.leaseplatform.sys.entity.SysOperationLog;
import com.example.leaseplatform.sys.mapper.SysOperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 操作日志查询（写入由 AOP 切面完成，见 sys.log.OperationLogAspect）。
 */
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final SysOperationLogMapper logMapper;

    /** 分页查询（操作人/操作类型筛选） */
    public PageResult<OperationLogVO> page(int page, int size, Long operatorId, String operationType) {
        Page<SysOperationLog> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        logMapper.selectPage(p, new LambdaQueryWrapper<SysOperationLog>()
                .eq(operatorId != null, SysOperationLog::getOperatorId, operatorId)
                .like(operationType != null && !operationType.isBlank(),
                        SysOperationLog::getOperationType, operationType)
                .orderByDesc(SysOperationLog::getId));
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    private OperationLogVO toVO(SysOperationLog entry) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(entry.getId());
        vo.setOperatorId(entry.getOperatorId());
        vo.setOperatorType(entry.getOperatorType());
        vo.setOperationType(entry.getOperationType());
        vo.setOperationContent(entry.getOperationContent());
        vo.setIpAddress(entry.getIpAddress());
        vo.setUserAgent(entry.getUserAgent());
        vo.setCreatedAt(TimeUtil.toEpochMillis(entry.getCreatedAt()));
        return vo;
    }
}
