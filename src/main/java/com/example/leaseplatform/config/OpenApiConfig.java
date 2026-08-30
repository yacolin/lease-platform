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
                                接口按端分组（springdoc group-configs，各端独立 JSON，便于生成前端请求文件）：
                                - 管理端（/v3/api-docs/admin）：管理员登录 + 分类/商品/菜单等后台接口
                                - 公开/小程序端（/v3/api-docs/public）：公开浏览 + 微信登录/刷新/登出 + 我的资料
                                - 全部（/v3/api-docs，Swagger UI 顶部按端切换）
                                标签命名约定：tag name 用英文驼峰（如 roomPublic / productAdmin），
                                前端按标签生成请求文件时文件名即标签名（中文会被转成拼音，故不用中文）；
                                中文说明统一写在 description（Swagger UI 标签下方可见）。
                                认证方式：请求头 Authorization: Bearer <accessToken>""")
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
