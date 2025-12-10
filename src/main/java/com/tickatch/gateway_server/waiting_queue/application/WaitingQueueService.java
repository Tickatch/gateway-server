package com.tickatch.gateway_server.waiting_queue.application;

import static com.tickatch.gateway_server.waiting_queue.application.exception.QueueErrorCode.INVALID_QUEUE_TOKEN;

import com.tickatch.gateway_server.global.util.HmacUtil;
import com.tickatch.gateway_server.waiting_queue.application.dto.LineUpResponse;
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

  public Mono<LineUpResponse> lineUp(String userId) {
    long timestamp = System.currentTimeMillis();
    String token = HmacUtil.hmacSha26(secretKey, userId + ":" + timestamp);

    return queueRepository.lineUp(token).thenReturn(new LineUpResponse(timestamp, token));
  }

  public Mono<Boolean> canEnter(String token, String userId, String timestamp) {
    validateToken(token, userId, timestamp);
    return queueRepository.isAlreadyAllowedIn(token);
  }

  public Mono<QueueStatusResponse> getStatus(String token) {
    return queueRepository.getCurrentStatus(token);
  }

  public Mono<Void> admitNextPerson() {
    return queueRepository.allowNextUser();
  }

  private void validateToken(String token, String userId, String timestamp) {
    String recreatedToken = HmacUtil.hmacSha26(secretKey, userId + ":" + timestamp);

    if (!token.equals(recreatedToken)) {
      throw new QueueException(INVALID_QUEUE_TOKEN);
    }
  }
}
