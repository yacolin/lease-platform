package com.example.leaseplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用入口。
 *
 * <p>{@code @EnableScheduling} 供定时任务使用，目前有：
 * {@link com.example.leaseplatform.mtg.service.MeetingReservationExpiryJob}
 * （会议室预约过期清理，原先挂在 GET 接口上惰性执行）。
 */
@SpringBootApplication
@EnableScheduling
public class LeasePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeasePlatformApplication.class, args);
    }

}
