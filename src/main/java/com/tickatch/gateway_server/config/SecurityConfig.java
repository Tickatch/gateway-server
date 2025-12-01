package com.tickatch.gateway_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Actuator 엔드포인트 허용
                        .pathMatchers("/actuator/**").permitAll()
                        // Health Check
                        .pathMatchers("/health/**").permitAll()
                        // 모든 요청 허용 (추후 인증 설정 추가)
                        .anyExchange().permitAll()
                )
                .build();
    }
}