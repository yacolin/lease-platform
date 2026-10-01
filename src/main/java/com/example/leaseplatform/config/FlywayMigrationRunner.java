package com.example.leaseplatform.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Flyway 数据库迁移驱动（Spring Boot 4 已移除 Flyway 自动配置，需自行调用 Java API）。
 * 由 spring.flyway.enabled 控制（开发默认关闭，库由 ./reset_db.sh 全量重建；
 * 生产 profile 启用，全新库启动时自动执行 src/main/resources/db/migration V1~V11）。
 *
 * <p>{@code @Order(0)}：必须排在 {@link com.example.leaseplatform.common.cache.CacheWarmupRunner}
 * （{@code @Order(10)}）之前 —— 生产首次启动要先由本迁移建出表，缓存预热才读得到数据。
 */
@Slf4j
@Component
@Order(0)
public class FlywayMigrationRunner implements ApplicationRunner {

    private final DataSource dataSource;

    @Value("${spring.flyway.enabled:false}")
    private boolean enabled;

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String locations;

    public FlywayMigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Flyway 迁移已禁用（开发环境用 ./reset_db.sh 重建；生产设置 spring.flyway.enabled=true）");
            return;
        }
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .baselineOnMigrate(true)
                .load();
        org.flywaydb.core.api.MigrationInfoService info = flyway.info();
        log.info("Flyway 迁移开始，当前版本: {}", info.current() == null ? "无（空库）" : info.current().getVersion());
        int applied = flyway.migrate().migrationsExecuted;
        log.info("Flyway 迁移完成，共执行 {} 个迁移", applied);
    }
}
