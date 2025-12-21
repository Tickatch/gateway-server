package com.tickatch.gateway_server.waiting_queue.infrastructure.security;

import com.tickatch.gateway_server.global.api.MonoResponseHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

  private final MonoResponseHelper responseHelper;

  @Override
  public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
    return responseHelper.writeError(
        exchange, HttpStatus.UNAUTHORIZED, "USER_ID_REQUIRED", "로그인이 필요합니다."
    );
  }
}
