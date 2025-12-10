package com.tickatch.gateway_server.waiting_queue.application.port;

import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusResponse;
import reactor.core.publisher.Mono;

public interface QueueRepository {

  Mono<Void> lineUp(String token);

  Mono<QueueStatusResponse> getCurrentStatus(String token);

  Mono<Void> allowNextUser();

  Mono<Boolean> isAlreadyAllowedIn(String token);
}
