package com.tickatch.gateway_server.waiting_queue.infrastructure.filter;

import com.tickatch.gateway_server.global.api.MonoResponseHelper;
import com.tickatch.gateway_server.waiting_queue.application.WaitingQueueService;
import com.tickatch.gateway_server.waiting_queue.application.exception.QueueException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class QueueFilter implements WebFilter, Ordered {

  private final WaitingQueueService queueService;
  private final MonoResponseHelper responseHelper;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    // 나중에 userId는 jwt에서 가져오기
    String queueUserId = exchange.getRequest().getHeaders().getFirst("X-Queue-User-Id");
    String queueToken = exchange.getRequest().getHeaders().getFirst("X-Queue-Token");

    String path = exchange.getRequest().getPath().value();

    if (!StringUtils.hasText(queueUserId)) {
      return responseHelper.writeError(
          exchange, HttpStatus.UNAUTHORIZED, "USER_ID_REQUIRED", "로그인이 필요합니다."
      );
    }

    // 대기열 API 빼고 나머지 경로는 대기열 타기
    if (path.startsWith("/api/v1/queue/")) {
      return chain.filter(exchange);
    }

    // 대기열 토큰을 발급 안 했다면
    if (!StringUtils.hasText(queueToken)) {
      return responseHelper.writeError(
          exchange, HttpStatus.UNAUTHORIZED, "QUEUEING_REQUIRED", "대기열을 통해서 입장해주세요"
      );
    }

    // 입장 가능한지 redis를 조회
    return queueService.canEnter(queueToken, queueUserId)
        .flatMap(canEnter ->
            canEnter ? chain.filter(exchange) : rejectWithQueueInfo(exchange, queueToken)
        )
        .onErrorResume(  // 토큰 검증 실패
            QueueException.class,
            e -> responseHelper.writeError(exchange, HttpStatus.UNAUTHORIZED, e.getCode(), e.getMessage())
        );
  }

  // redis 해시에 입장 허용 필드가 없음.
  // 대기열에 토큰이 있는지 확인 -> 대기열에 있으면 대기 중인 사람, 없으면 토큰이 만료된 사람
  private Mono<Void> rejectWithQueueInfo(ServerWebExchange exchange, String token) {
    return queueService.getStatus(token)
        .flatMap(status -> responseHelper.writeSuccessWithStatus(
            exchange, HttpStatus.TOO_MANY_REQUESTS, status, "대기 중입니다.")
        )
        .onErrorResume(QueueException.class, e -> responseHelper.writeError(
            exchange, HttpStatus.FORBIDDEN, "NOT_IN_QUEUE", "대기열에 등록되지 않은 사용자입니다."
        ));
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
