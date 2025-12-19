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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
      @RequestHeader("X-User-Id") String userId) {

    log.info("SSE 연결 시작 - userId: {}", userId);

    // 1. 초기 상태 확인
    // 바로 입장 가능한 경우 SSE 구독 안 시킴
    Mono<ServerSentEvent<Object>> initialStatus = getInitialStatus(userId);

    // 2. SSE 구독
    Flux<ServerSentEvent<Object>> statusUpdates = subscribeToUpdates(userId);

    // 3. 연결 유지를 위한 heartbeat 보내기 (30초마다)
    Flux<ServerSentEvent<Object>> heartbeat = Flux.interval(Duration.ofSeconds(30))
        .map(tick -> ServerSentEvent.builder()
            .event("HEARTBEAT")
            .data(new HeartbeatEvent(System.currentTimeMillis()))
            .build());

    // 4. 초기 상태 + 업데이트 스트림 + heartbeat를 결합해서 반환
    return Flux.concat(initialStatus, Flux.merge(statusUpdates, heartbeat))
        .doOnCancel(() -> {
          log.info("SSE 연결 종료 - userId: {}", userId);
          queueStatusNotifier.unsubscribe(userId);
        })
        .doOnError(e -> {
          log.error("SSE 스트림 에러 - userId: {}", userId, e);
          queueStatusNotifier.unsubscribe(userId);
        });
  }

  private Flux<ServerSentEvent<Object>> subscribeToUpdates(String userId) {
    return queueStatusNotifier
        .subscribe(userId)
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
            return ServerSentEvent.builder()
                .event("UNKNOWN")
                .data(event)
                .build();
          }
        });
  }

  private Mono<ServerSentEvent<Object>> getInitialStatus(String userId) {
    return queueService.canEnter(userId)
        .flatMap(canEnter -> {
          if (canEnter) {
            return Mono.just(ServerSentEvent.builder()
                .event("ALLOWED_IN")
                .data(new AllowedInEvent("입장 가능합니다."))
                .build());
          } else {
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
        });
  }
}
