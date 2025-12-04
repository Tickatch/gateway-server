package com.tickatch.gateway_server.waiting_queue.infrastructure.filter;

import com.tickatch.gateway_server.global.api.MonoResponseHelper;
import com.tickatch.gateway_server.waiting_queue.application.QueueService;
import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusResponse;
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

  private final QueueService queueService;
  private final MonoResponseHelper responseHelper;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
    String path = exchange.getRequest().getPath().value();

    if (!StringUtils.hasText(userId)) {
      return responseHelper.writeError(
          exchange, HttpStatus.UNAUTHORIZED, "USER_ID_REQUIRED", "로그인이 필요합니다."
      );
    }

    // 대기열 API 빼고 나머지 경로는 대기열 타기
    if (path.startsWith("/api/v1/queue/")) {
      return chain.filter(exchange);
    }

    if (queueService.canEnter(userId)) {
      return chain.filter(exchange);
    } else {
      return rejectWithQueueInfo(exchange, userId);
    }
  }

  private Mono<Void> rejectWithQueueInfo(ServerWebExchange exchange, String userId) {
    try {
      QueueStatusResponse status = queueService.getStatus(userId);

      return responseHelper.writeSuccessWithStatus(
          exchange, HttpStatus.TOO_MANY_REQUESTS, status, "대기 중입니다."
      );
    } catch (IllegalStateException e) {
      return responseHelper.writeError(
          exchange, HttpStatus.FORBIDDEN, "NOT_IN_QUEUE", "대기열에 등록되지 않은 사용자입니다."
      );
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
