package com.tickatch.gateway_server.waiting_queue.application;

import static com.tickatch.gateway_server.waiting_queue.application.exception.QueueErrorCode.INVALID_QUEUE_TOKEN;

import com.tickatch.gateway_server.global.util.HmacUtil;
import com.tickatch.gateway_server.waiting_queue.application.dto.LineUpResult;
import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusResponse;
import com.tickatch.gateway_server.waiting_queue.application.exception.QueueException;
import com.tickatch.gateway_server.waiting_queue.application.port.QueueRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class WaitingQueueService {

  private final QueueRepository queueRepository;
  private final String secretKey;

  public WaitingQueueService(
      QueueRepository queueRepository,
      @Value("${queue.secret-key}") String secretKey) {
    this.queueRepository = queueRepository;
    this.secretKey = secretKey;
  }

  public Mono<LineUpResult> lineUp(String userId) {
    String token = HmacUtil.hmacSha26(secretKey, userId);
    return checkAlreadyAllowedIn(token)
        .switchIfEmpty(checkAlreadyInQueue(token))
        .switchIfEmpty(enqueuedNewUser(token));
  }

  public Mono<Boolean> canEnter(String token, String userId) {
    validateToken(token, userId);
    return queueRepository.isAlreadyAllowedIn(token);
  }

  public Mono<QueueStatusResponse> getStatus(String token) {
    return queueRepository.getCurrentStatus(token);
  }

  public Mono<Void> admitNextPerson() {
    return queueRepository.allowNextUser();
  }

  private void validateToken(String token, String userId) {
    String recreatedToken = HmacUtil.hmacSha26(secretKey, userId);

    if (!token.equals(recreatedToken)) {
      throw new QueueException(INVALID_QUEUE_TOKEN);
    }
  }

  private Mono<LineUpResult> checkAlreadyAllowedIn(String token) {
    return queueRepository.isAlreadyAllowedIn(token)
        .filter(Boolean::booleanValue) // False면 자동으로 Mono.empty()를 반환
        .map(isAllowed -> new LineUpResult(token, "이미 입장 가능한 상태입니다."));
  }

  private Mono<LineUpResult> checkAlreadyInQueue(String token) {
    return queueRepository.isInQueue(token)
        .filter(Boolean::booleanValue)
        .map(isAllowed -> new LineUpResult(token, "이미 대기 중입니다."));
  }

  private Mono<LineUpResult> enqueuedNewUser(String token) {
    return queueRepository.lineUp(token).then(Mono.just(new LineUpResult(token, "대기열에 등록되었습니다.")));
  }
}
