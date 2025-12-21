package com.tickatch.gateway_server.waiting_queue.application;

import com.tickatch.gateway_server.waiting_queue.application.dto.AllowedInNotificationEvent;
import com.tickatch.gateway_server.waiting_queue.application.dto.QueueEvent;
import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusChangeEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

@Slf4j
@Component
public class QueueStatusNotifier {

  private final Map<String, Many<QueueEvent>> userSinks = new ConcurrentHashMap<>();

  // 특정 사용자의 대기열 상태 변경 이벤트를 구독
  public Flux<QueueEvent> subscribe(String userId) {
    log.info("사용자 구독 시작 - userID: {}", userId);

    Many<QueueEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
    userSinks.put(userId, sink);

    return sink.asFlux()
        .doFinally(signalType -> {
          log.info("사용자 구독 종료 - userId: {}, signal: {}", userId, signalType);
          userSinks.remove(userId);
        });
  }

  // 구독 해제
  public void unsubscribe(String userId) {
    Many<QueueEvent> sink = userSinks.remove(userId);
    if (sink != null) {
      sink.tryEmitComplete();
    }
  }

  // 특정 사용자에게 대기열 상태 변경 알림
  public void notifyStatusChange(String userId, QueueStatusChangeEvent event) {
    Many<QueueEvent> sink = userSinks.get(userId);
    if (sink != null) {
      Sinks.EmitResult result = sink.tryEmitNext(event);
      if (result.isFailure()) {
        log.warn("이벤트 전송 실패 - userId: {}, result: {}", userId, result);
      }
    }
  }

  // 특정 사용자에게 입장 허용 알림
  public void notifyAllowedIn(String userId) {
    log.info("userSinks={}", userSinks.get(userId));
    Many<QueueEvent> sink = userSinks.get(userId);
    if (sink != null) {
      Sinks.EmitResult result = sink.tryEmitNext(new AllowedInNotificationEvent());
      if (result.isFailure()) {
        log.warn("입장 허용 알림 전송 실패 - userId: {}", userId);
      } else {
        log.info("입장 허용 알림 전송 완료 - userId: {}", userId);
      }
    }
  }

  public int getActiveSubscribers() {
    return userSinks.size();
  }
}
