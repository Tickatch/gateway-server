package com.tickatch.gateway_server.waiting_queue.application.port;

import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusResponse;
import reactor.core.publisher.Mono;

public interface QueueRepository {

  Mono<String> lineUp(String token);

  Mono<QueueStatusResponse> getCurrentStatus(String token);

  Mono<Boolean> isAlreadyAllowedIn(String token);

  Mono<Boolean> removeAllowedToken(String token);

  Mono<Void> refreshAllowedInTimestamp(String token);

  Mono<Void> cleanupExpiredTokens();

  Mono<Boolean> removeWaitingToken(String token);
}
