package com.example.leaseplatform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI 配置。
 * 文档地址：http://localhost:8080/swagger-ui.html（Security 白名单已放行）
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI leasePlatformOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("租赁平台 API")
                        .description("""
                                租赁小程序平台 1.0 接口文档。
                                - /api/v1/public/**：公开浏览接口，无需认证
                                - /api/v1/**：商家后台管理接口，需 Bearer Token（JWT 接入后生效）""")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Access Token，格式：Bearer <token>")));
    }
}
