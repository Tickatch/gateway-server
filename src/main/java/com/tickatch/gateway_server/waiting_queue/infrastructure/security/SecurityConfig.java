package com.tickatch.gateway_server.waiting_queue.infrastructure.security;

import com.tickatch.gateway_server.global.api.MonoResponseHelper;
import com.tickatch.gateway_server.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  @Bean
  public JwtAuthenticationFilter jwtAuthenticationFilter() {
    return new JwtAuthenticationFilter();
  }

  @Bean
  public SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      AuthenticationEntryPoint authenticationEntryPoint
  ) {
    return http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(exchanges -> exchanges
            // Actuator 엔드포인트 허용
            .pathMatchers("/actuator/**").permitAll()
            // Health Check
            .pathMatchers("/health/**").permitAll()
            // Swagger / OpenAPI
            .pathMatchers("/swagger-ui/**").permitAll()
            .pathMatchers("/swagger-ui.html").permitAll()
            .pathMatchers("/v3/api-docs/**").permitAll()
            // 각 서비스별 API docs
            .pathMatchers("/auth-service/v3/api-docs/**").permitAll()
            .pathMatchers("/product-service/v3/api-docs/**").permitAll()
            .pathMatchers("/reservation-service/v3/api-docs/**").permitAll()
            .pathMatchers("/reservation-seat-service/v3/api-docs/**").permitAll()
            .pathMatchers("/arthall-service/v3/api-docs/**").permitAll()
            .pathMatchers("/ticket-service/v3/api-docs/**").permitAll()

            // JWKS (Public Key 공개)
            .pathMatchers("/.well-known/**").permitAll()

            // ========================================
            // Auth Service - 공개 API
            // ========================================
            .pathMatchers("/api/v1/auth/login").permitAll()
            .pathMatchers("/api/v1/auth/register").permitAll()
            .pathMatchers("/api/v1/auth/refresh").permitAll()
            .pathMatchers("/api/v1/auth/check-email").permitAll()
            .pathMatchers("/api/v1/auth/me").permitAll()

            // OAuth - 로그인/콜백만 공개 (link, unlink는 인증 필요)
            .pathMatchers(HttpMethod.GET, "/api/v1/auth/oauth/*/callback").permitAll()
            .pathMatchers(HttpMethod.GET, "/api/v1/auth/oauth/*").permitAll()

            // 대기열 API도 인증 필수
            .pathMatchers("/api/v1/queue/**").authenticated()

            // 예매 관련 API는 인증 필수
            .pathMatchers(HttpMethod.POST, "/api/v1/reservations").authenticated()
            .pathMatchers(HttpMethod.POST, "/api/v1/reservation-seats/**").authenticated()

            // 나머지는 요청 허용 (추후 인증 설정 추가)
            .anyExchange().permitAll()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
        .addFilterAfter(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
        .build();
  }
}