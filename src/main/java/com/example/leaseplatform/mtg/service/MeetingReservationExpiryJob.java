package com.example.leaseplatform.mtg.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 会议室预约过期清理定时任务。
 *
 * <p>背景：过期清理原先挂在查询接口上（{@code GET /me/meeting-reservations} 与
 * {@code GET /meeting-reservations} 各自调用一次惰性清理）。该做法有三个问题：
 * <ol>
 *   <li><b>GET 里写库</b>：读接口产生写操作，且与查询不在同一事务内；</li>
 *   <li><b>无界</b>：{@code reservation_date < CURDATE()} 没有日期下限也没有 LIMIT，
 *       历史越久每次列表刷新要扫的行越多；</li>
 *   <li><b>N+1 且无事务</b>：批量置过期后，逐条释放占用、逐条解冻、逐条取消订单，
 *       最坏 {@code 5 + 3E + 3Ep} 条 SQL；循环中途失败会留下
 *       「预约已过期但占用未释放 / 余额仍冻结」的不一致。</li>
 * </ol>
 *
 * <p>现改为：定时任务按批调用 {@link MeetingReservationService#expirePastReservationsOnce(int)}，
 * 每批独立事务、有界处理；处理过的记录状态变为「已过期」，自动退出
 * {@code status IN (0,1)} 过滤，因此反复调用即可逐步排空积压。
 *
 * <p>配置（{@code application.yml} 的 {@code lease.mtg.expiry-job}）：
 * <ul>
 *   <li>{@code enabled}：是否启用，默认 true；</li>
 *   <li>{@code initial-delay-ms}：启动后首次执行的延迟，默认 60000（避开启动期与测试窗口）；</li>
 *   <li>{@code interval-ms}：两次执行的间隔，默认 300000（5 分钟）；</li>
 *   <li>{@code batch-size}：单批条数，默认 {@link MeetingReservationService#EXPIRE_DEFAULT_BATCH_SIZE}。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lease.mtg.expiry-job.enabled", havingValue = "true", matchIfMissing = true)
public class MeetingReservationExpiryJob {

    /** 单次运行最多处理多少批（防止积压时单次运行时间过长，剩余留给下一轮） */
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final MeetingReservationService reservationService;

    @Scheduled(
            initialDelayString = "${lease.mtg.expiry-job.initial-delay-ms:60000}",
            fixedDelayString = "${lease.mtg.expiry-job.interval-ms:300000}")
    public void expirePastReservations() {
        int total = 0;
        try {
            for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
                int handled = reservationService.expirePastReservationsOnce(
                        MeetingReservationService.EXPIRE_DEFAULT_BATCH_SIZE);
                total += handled;
                // 本批未满，说明已无待过期数据，提前结束
                if (handled < MeetingReservationService.EXPIRE_DEFAULT_BATCH_SIZE) {
                    break;
                }
            }
        } catch (Exception e) {
            // 定时任务不可因单次异常而终止（Spring 会记录但不影响后续调度，这里显式记录上下文）
            log.error("会议室预约过期清理失败（本轮已处理 {} 条，下轮将继续）", total, e);
            return;
        }
        if (total > 0) {
            log.info("会议室预约过期清理完成，本轮共处理 {} 条", total);
        }
    }
}
