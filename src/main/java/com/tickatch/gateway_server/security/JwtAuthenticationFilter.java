package com.tickatch.gateway_server.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * JWT에서 사용자 정보를 추출하여 X-User-Id, X-User-Type 헤더로 전달하는 필터.
 *
 * <p>Spring Security OAuth2 Resource Server에서 JWT 검증 후
 * SecurityContext에 저장된 인증 정보를 사용한다.
 *
 * <p>추출 정보:
 * <ul>
 *   <li>sub (subject) → X-User-Id</li>
 *   <li>userType claim → X-User-Type</li>
 * </ul>
 *
 * @author Tickatch
 * @since 1.0.0
 */
@Slf4j
public class JwtAuthenticationFilter implements WebFilter {

  private static final String HEADER_USER_ID = "X-User-Id";
  private static final String HEADER_USER_TYPE = "X-User-Type";
  private static final String CLAIM_USER_TYPE = "userType";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    return ReactiveSecurityContextHolder.getContext()
        .filter(context -> context.getAuthentication() != null)
        .filter(context -> context.getAuthentication().isAuthenticated())
        .filter(context -> context.getAuthentication() instanceof JwtAuthenticationToken)
        .map(context -> (JwtAuthenticationToken) context.getAuthentication())
        .map(JwtAuthenticationToken::getToken)
        .flatMap(jwt -> {
          // JWT에서 사용자 정보 추출
          String userId = jwt.getSubject();
          String userType = jwt.getClaimAsString(CLAIM_USER_TYPE);

          log.info("JWT 인증 정보 추출 - userId: {}, userType: {}", userId, userType);

          // SecurityContext에 userId 저장


          // 헤더에 사용자 정보 추가
          ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
              .header(HEADER_USER_ID, userId)
              .header(HEADER_USER_TYPE, userType != null ? userType : "")
              .build();

          ServerWebExchange mutatedExchange = exchange.mutate()
              .request(mutatedRequest)
              .build();
          log.info("jwt필터 익스체인지 해시코드:{}", System.identityHashCode(mutatedExchange));
          return chain.filter(mutatedExchange);
        })
        // 인증 정보가 없으면 그냥 통과 (permitAll 엔드포인트)
        .switchIfEmpty(chain.filter(exchange));
  }
}