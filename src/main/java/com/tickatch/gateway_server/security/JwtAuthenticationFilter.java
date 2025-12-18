package com.tickatch.gateway_server.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
public class JwtAuthenticationFilter implements WebFilter {

  private static final String HEADER_USER_ID = "X-User-Id";
  private static final String HEADER_USER_TYPE = "X-User-Type";
  private static final String CLAIM_USER_TYPE = "userType";
  private static final String JWT_FILTER_APPLIED = "JWT_FILTER_APPLIED";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String requestId = exchange.getRequest().getId();
    String path = exchange.getRequest().getPath().value();

    // 이미 실행되었다면 스킵
    Boolean alreadyApplied = exchange.getAttribute(JWT_FILTER_APPLIED);
    if (Boolean.TRUE.equals(alreadyApplied)) {
      return chain.filter(exchange);
    }

    // 실행했음을 표시
    exchange.getAttributes().put(JWT_FILTER_APPLIED, true);

    return ReactiveSecurityContextHolder.getContext()
        .filter(context -> context.getAuthentication() != null)
        .filter(context -> context.getAuthentication().isAuthenticated())
        .filter(context -> context.getAuthentication() instanceof JwtAuthenticationToken)
        .map(context -> (JwtAuthenticationToken) context.getAuthentication())
        .map(JwtAuthenticationToken::getToken)
        .flatMap(jwt -> {
          String userId = jwt.getSubject();
          String userType = jwt.getClaimAsString(CLAIM_USER_TYPE);

          // 헤더에 사용자 정보 추가
          ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
              .header(HEADER_USER_ID, userId)
              .header(HEADER_USER_TYPE, userType != null ? userType : "")
              .build();

          ServerWebExchange mutatedExchange = exchange.mutate()
              .request(mutatedRequest)
              .build();

          return chain.filter(mutatedExchange);
        })
        // 인증 정보가 없으면 그냥 통과 (permitAll 엔드포인트)
        .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)));
  }
}