package com.tickatch.gateway_server.waiting_queue.application;

import com.tickatch.gateway_server.global.util.HmacUtil;
import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusResponse;
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

  public Mono<String> lineUp(String userId) {
    String token = getQueueToken(userId);
    return queueRepository.lineUp(token);
  }

  public Mono<Boolean> canEnter(String userId) {
    String token = getQueueToken(userId);
    return queueRepository.isAlreadyAllowedIn(token);
  }

  public Mono<QueueStatusResponse> getStatus(String userId) {
    String token = getQueueToken(userId);
    return queueRepository.getCurrentStatus(token);
  }

  public Mono<Void> refreshAllowedInTimeStamp(String userId) {
    String token = getQueueToken(userId);
    return queueRepository.refreshAllowedInTimestamp(token);
  }

  public Mono<Boolean> removeAllowedToken(String userId) {
    String token = getQueueToken(userId);
    return queueRepository.removeAllowedToken(token);
  }

  public Mono<Void> cleanupExpiredTokens() {
    return queueRepository.cleanupExpiredTokens();
  }

  public Mono<Boolean> removeWaitingToken(String userId) {
    String token = getQueueToken(userId);
    return queueRepository.removeWaitingToken(token);
  }

  private String getQueueToken(String userId) {
    return HmacUtil.hmacSha26(secretKey, userId);
  }
}
