package com.banking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

        private final JwtAuthInterceptor jwtAuthInterceptor;
        private final AdminAuthInterceptor adminAuthInterceptor;

        public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor, AdminAuthInterceptor adminAuthInterceptor) {
                this.jwtAuthInterceptor = jwtAuthInterceptor;
                this.adminAuthInterceptor = adminAuthInterceptor;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
                // Protect all customer resource endpoints while keeping registration and login
                // open
                registry.addInterceptor(jwtAuthInterceptor)
                                .addPathPatterns("/api/customers/**")
                                .excludePathPatterns(
                                                "/api/customers/register",
                                                "/api/customers/login");

                // Protect all admin resource endpoints while keeping admin login open
                registry.addInterceptor(adminAuthInterceptor)
                                .addPathPatterns("/api/admin/**")
                                .excludePathPatterns(
                                                "/api/admin/login");
        }
}
