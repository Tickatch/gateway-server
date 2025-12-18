package com.tickatch.gateway_server.waiting_queue.infrastructure.filter;

import com.tickatch.gateway_server.global.api.MonoResponseHelper;
import com.tickatch.gateway_server.waiting_queue.application.WaitingQueueService;
import com.tickatch.gateway_server.waiting_queue.application.exception.QueueException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueueFilter implements GlobalFilter, Ordered {

  private static final String QUEUE_FILTER_APPLIED = "QUEUE_FILTER_APPLIED";

  private final WaitingQueueService queueService;
  private final MonoResponseHelper responseHelper;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    HttpMethod method = exchange.getRequest().getMethod();

    // 해당 요청에서 큐 필터를 이미 한 번 통과했다면 스킵
    Boolean alreadyApplied = exchange.getAttribute(QUEUE_FILTER_APPLIED);
    if (Boolean.TRUE.equals(alreadyApplied)) {
      return chain.filter(exchange);
    }

    // 응답이 이미 커밋되었다면 스킵
    if (exchange.getResponse().isCommitted()) {
      return chain.filter(exchange);
    }

    // 큐 필터를 통과했다는 기록을 속성에 남김
    exchange.getAttributes().put(QUEUE_FILTER_APPLIED, true);

    String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

    // 1. 화이트리스트 체크
    if (isWhitelistPath(path, method)) {
      return chain.filter(exchange);
    }

    // 2. 로그인 체크
    if (!StringUtils.hasText(userId)) {
      return responseHelper.writeError(
          exchange, HttpStatus.UNAUTHORIZED, "USER_ID_REQUIRED", "로그인이 필요합니다."
      );
    }

    // 3. 입장 가능한지 체크 (예매 관련 API만 대기열 적용)
    // 입장 가능: 입장 허용 타임스탬프 갱신 + 요청 통과
    // 입장 불가: 대기열 상태 반환
    if (isReservationPath(path, method)){
      return queueService.canEnter(userId)
          .flatMap(canEnter -> {
            if (!canEnter) {
              return rejectWithQueueInfo(exchange, userId);
            }

            return queueService.refreshAllowedInTimeStamp(userId).then(chain.filter(exchange));
          });
    }

    // 4. 그 외 API는 통과
    return chain.filter(exchange);
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
    switch (path) {
      // Auth 기본 API
      case "/api/v1/auth/login":
      case "/api/v1/auth/register":
      case "/api/v1/auth/refresh":
      case "/api/v1/auth/check-email":
      case "/api/v1/auth/me":
        return true;

      default:
        // OAuth
        if (method == HttpMethod.GET &&
            path.matches("^/api/v1/auth/oauth/[^/]+$")) {
          return true;
        }

        if (method == HttpMethod.GET &&
            path.matches("^/api/v1/auth/oauth/[^/]+/callback$")) {
          return true;
        }

        return false;
    }
  }

  private boolean isReservationPath(String path, HttpMethod method) {
    if (method != HttpMethod.POST) {
      return false;
    }

    return path.startsWith("/api/v1/reservation-seats") || path.equals("/api/v1/reservations");
  }

  @Override
  public int getOrder() {
    return -1;
  }
}