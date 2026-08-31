package com.example.leaseplatform.sys.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.leaseplatform.sys.entity.SysIdempotency;
import com.example.leaseplatform.sys.mapper.SysIdempotencyMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 幂等服务单元测试（1.2）：acquire 去重（含并发唯一键兜底）/ complete / sha256。
 */
@ExtendWith(MockitoExtension.class)
class SysIdempotencyServiceTest {

    @Mock
    private SysIdempotencyMapper mapper;

    private SysIdempotencyService service;

    @BeforeAll
    static void initMpEntityCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SysIdempotency.class);
    }

    @BeforeEach
    void setUp() {
        service = new SysIdempotencyService(mapper);
    }

    @Test
    void acquire_newKey_shouldInsertAndReturnTrue() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(mapper.insert(any(SysIdempotency.class))).thenReturn(1);

        boolean acquired = service.acquire("WX_NOTIFY:RC123", "RECHARGE_NOTIFY", 100L,
                "abc", LocalDateTime.now().plusDays(7));

        assertThat(acquired).isTrue();
        verify(mapper).insert(any(SysIdempotency.class));
    }

    @Test
    void acquire_existingKey_shouldReturnFalse() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(new SysIdempotency());

        boolean acquired = service.acquire("WX_NOTIFY:RC123", "RECHARGE_NOTIFY", 100L, "abc", null);

        assertThat(acquired).isFalse();
        verify(mapper, never()).insert(any(SysIdempotency.class));
    }

    @Test
    void acquire_concurrentDuplicate_shouldReturnFalse() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(mapper.insert(any(SysIdempotency.class))).thenThrow(new DuplicateKeyException("dup"));

        boolean acquired = service.acquire("WX_NOTIFY:RC123", "RECHARGE_NOTIFY", 100L, "abc", null);

        assertThat(acquired).isFalse();
    }

    @Test
    void complete_shouldMarkSuccess() {
        when(mapper.update(any(), any(Wrapper.class))).thenReturn(1);

        service.complete("WX_NOTIFY:RC123", "OK");

        verify(mapper).update(any(), any(Wrapper.class));
    }

    @Test
    void sha256_shouldBeDeterministicAndHex() {
        String h1 = SysIdempotencyService.sha256("hello");
        String h2 = SysIdempotencyService.sha256("hello");
        assertThat(h1).isEqualTo(h2).hasSize(64);
        assertThat(SysIdempotencyService.sha256("hello"))
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }
}
