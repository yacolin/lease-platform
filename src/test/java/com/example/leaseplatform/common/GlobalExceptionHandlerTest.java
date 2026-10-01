package com.example.leaseplatform.common;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局异常处理单测。
 *
 * <p>重点覆盖唯一键冲突 → 409：项目里多处采用「先 selectCount 查重再 insert」的写法，
 * 其查重只是启发式（TOCTOU 竞态），并发下最终由唯一索引拦下其中一个。
 * 若不加此映射，竞态输家会落入兜底的 Exception 分支变成 500（见 docs 评估 §2.6）。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void duplicateKey_shouldMapTo409Conflict() {
        ResponseEntity<ApiResponse<Void>> resp = handler.handleDuplicateKey(
                new DuplicateKeyException("Duplicate entry '1-VIP' for key 'uk_room_level'"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(ErrorCode.CONFLICT);
        // 不向外暴露具体是哪个唯一键
        assertThat(resp.getBody().getMessage()).doesNotContain("uk_room_level");
    }

    @Test
    void unexpectedException_shouldStillMapTo500() {
        ResponseEntity<ApiResponse<Void>> resp = handler.handleException(new IllegalStateException("boom"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void bizException_shouldKeepItsOwnStatus() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleBizException(BizException.conflict("该会议室已配置该等级的定价"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(resp.getBody().getMessage()).isEqualTo("该会议室已配置该等级的定价");
    }
}
