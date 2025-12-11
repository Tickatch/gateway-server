package com.tickatch.gateway_server.waiting_queue.infrastructure.filter;

import com.tickatch.gateway_server.global.api.MonoResponseHelper;
import com.tickatch.gateway_server.waiting_queue.application.WaitingQueueService;
import com.tickatch.gateway_server.waiting_queue.application.exception.QueueException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;


@RequiredArgsConstructor
@Slf4j
public class QueueFilter implements WebFilter{

  private final WaitingQueueService queueService;
  private final MonoResponseHelper responseHelper;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    log.info("필터 익스체인지 해시코드:{}", System.identityHashCode(exchange));

    String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

    log.info("헤더:{}", exchange.getRequest().getHeaders());

    String path = exchange.getRequest().getPath().value();

    // 1. 로그인 관련 경로는 무조건 통과시킴
    if (isWhitelistPath(path, exchange.getRequest().getMethod())) {
      return chain.filter(exchange);
    }

    log.info("사용자 userId={}", userId);

    // 2. 로그인 했는지 체크
    if (!StringUtils.hasText(userId)) {
      return responseHelper.writeError(
          exchange, HttpStatus.UNAUTHORIZED, "USER_ID_REQUIRED", "로그인이 필요합니다."
      );
    }

    // 3. 로그인을 했다면 대기열 API는 통과시킴
    if (path.startsWith("/api/v1/queue/")) {
      return chain.filter(exchange);
    }

    // 4. 입장 가능한지 redis 체크
    // 입장 가능: 입장 허용 타임스탬프 갱신 + 요청 통과
    // 입장 불가: 대기열 상태 반환
    return queueService.canEnter(userId)
        .flatMap(canEnter ->
            canEnter ? queueService.refreshAllowedInTimeStamp(userId)
                .then(chain.filter(exchange)) : rejectWithQueueInfo(exchange, userId)
        );
  }

  // redis 입장 허용 해시에 필드가 없다면
  // 대기열에 토큰이 있는지 확인 -> 대기열에 있으면 대기 중인 사람, 없으면 토큰이 만료된 사람
  private Mono<Void> rejectWithQueueInfo(ServerWebExchange exchange, String userId) {
    return queueService.getStatus(userId)
        .flatMap(status -> responseHelper.writeSuccessWithStatus(
            exchange, HttpStatus.TOO_MANY_REQUESTS, status, "대기 중입니다.")
        )
        .onErrorResume(QueueException.class, e -> responseHelper.writeError(
            exchange, HttpStatus.FORBIDDEN, "NOT_IN_QUEUE", "대기열에 등록되지 않은 사용자입니다."
        ));
  }

  private boolean isWhitelistPath(String path, HttpMethod method) {
    // Auth 기본 API
    if (path.equals("/api/v1/auth/login")) {
      return true;
    }
    if (path.equals("/api/v1/auth/register")) {
      return true;
    }
    if (path.equals("/api/v1/auth/refresh")) {
      return true;
    }
    if (path.equals("/api/v1/auth/check-email")) {
      return true;
    }
    if (path.equals("/api/v1/auth/me")) {
      return true;
    }

    // OAuth
    if (method == HttpMethod.GET && path.matches("^/api/v1/auth/oauth/[^/]+$")) {
      return true;
    }

    if (method == HttpMethod.GET && path.matches("^/api/v1/auth/oauth/[^/]+/callback$")) {
      return true;
    }

    return false;
  }

}
