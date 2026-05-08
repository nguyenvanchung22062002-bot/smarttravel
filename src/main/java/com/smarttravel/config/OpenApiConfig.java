package com.smarttravel.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI smartTravelOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("SmartTravel API (Author: Nguyen Van Trung)")
                        .description("""
                                ## Nền tảng đặt tour du lịch SmartTravel
                                
                                REST API cho hệ thống đặt tour, quản lý booking và thanh toán trực tuyến.
                                
                                ### Xác thực
                                1. Gọi `POST /api/auth/login` để lấy JWT token
                                2. Nhấn nút **Authorize** ở trên, nhập: `Bearer <token>`
                                3. Tất cả request có khóa 🔒 sẽ tự động đính kèm token
                                
                                ### Phân quyền
                                - **Public**: không cần token
                                - **USER**: cần đăng nhập
                                - **ADMIN**: cần tài khoản admin
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SmartTravel Team")
                                .email("support@smarttravel.vn"))
                        .license(new License()
                                .name("Đồ án tốt nghiệp - 2026")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development"),
                        new Server().url("https://smarttravel.vn").description("Production (demo)")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Nhập JWT token lấy từ /api/auth/login")));
    }
}
