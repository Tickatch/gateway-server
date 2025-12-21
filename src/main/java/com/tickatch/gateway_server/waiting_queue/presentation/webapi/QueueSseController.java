package com.tickatch.gateway_server.waiting_queue.presentation.webapi;

import com.tickatch.gateway_server.waiting_queue.application.QueueStatusNotifier;
import com.tickatch.gateway_server.waiting_queue.application.WaitingQueueService;
import com.tickatch.gateway_server.waiting_queue.application.dto.AllowedInNotificationEvent;
import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusChangeEvent;
import com.tickatch.gateway_server.waiting_queue.application.exception.QueueException;
import com.tickatch.gateway_server.waiting_queue.presentation.dto.AllowedInEvent;
import com.tickatch.gateway_server.waiting_queue.presentation.dto.ErrorEvent;
import com.tickatch.gateway_server.waiting_queue.presentation.dto.HeartbeatEvent;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
@Slf4j
public class QueueSseController {

  private final WaitingQueueService queueService;
  private final QueueStatusNotifier queueStatusNotifier;

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<Object>> streamQueueStatus(
      @AuthenticationPrincipal Jwt jwt) {

    String userId = jwt.getSubject();
    log.info("SSE 연결 시작 - userId: {}", userId);

    return queueService.canEnter(userId)
        .flatMapMany(canEnter -> {
          if (canEnter) {
            // 이미 입장 가능 -> ALLOWED_IN 이벤트만 보내고 완료
            return Flux.just(ServerSentEvent.builder()
                .event("ALLOWED_IN")
                .data(new AllowedInEvent("입장 가능합니다."))
                .build());
          } else {
            // 대기 중 -> 초기 상태 + 업데이트 스트림 + heartbeat
            Mono<ServerSentEvent<Object>> initialStatus = getInitialStatus(userId);

            Flux<ServerSentEvent<Object>> statusUpdates = subscribeToUpdates(userId);

            Flux<ServerSentEvent<Object>> heartbeat = Flux.interval(Duration.ofSeconds(30))
                .map(tick -> ServerSentEvent.builder()
                    .event("HEARTBEAT")
                    .data(new HeartbeatEvent(System.currentTimeMillis()))
                    .build());

            // merge() = 여러 Publisher를 동시에 구독해서, 도착하는 대로 섞어서 발행 (순서 보장 X)
            Flux<ServerSentEvent<Object>> updates = Flux.merge(statusUpdates, heartbeat)
                .takeUntil(sse -> "ALLOWED_IN".equals(sse.event()));

            // concat() = 앞 Publisher가 완전히 끝난 후에 다음 Publisher를 구독 (순서 보장 O)
            return Flux.concat(initialStatus, updates);
          }
        })
        .doFinally(signalType -> {
          log.info("SSE 종료 - userId: {}, 이유: {}", userId, signalType);
          queueStatusNotifier.unsubscribe(userId);
        })
        .doOnCancel(() -> {
          log.info("대기열에서 토큰 지우기");
          queueService.removeWaitingUserId(userId).subscribe();
        });
  }

  private Mono<ServerSentEvent<Object>> getInitialStatus(String userId) {
    return queueService.getStatus(userId)
        .map(status -> ServerSentEvent.builder()
            .event("STATUS_UPDATE")
            .data(status)
            .build())
        .onErrorResume(QueueException.class, e ->
            Mono.just(ServerSentEvent.builder()
                .event("ERROR")
                .data(new ErrorEvent("NOT_IN_QUEUE", "대기열에 등록되지 않은 사용자입니다."))
                .build())
        );
  }

  private Flux<ServerSentEvent<Object>> subscribeToUpdates(String userId) {
    return queueStatusNotifier.subscribe(userId)
        .map(event -> {
          if (event instanceof QueueStatusChangeEvent statusChange) {
            return ServerSentEvent.builder()
                .event("STATUS_UPDATE")
                .data(statusChange.getStatusResponse())
                .build();
          } else if (event instanceof AllowedInNotificationEvent) {
            return ServerSentEvent.builder()
                .event("ALLOWED_IN")
                .data(new AllowedInEvent("입장 가능합니다."))
                .build();
          } else {
            return ServerSentEvent.builder().event("UNKNOWN").data(event).build();
          }
        });
  }
}
