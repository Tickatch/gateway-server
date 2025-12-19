package com.tickatch.gateway_server.waiting_queue.application.port;

import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusResponse;
import com.tickatch.gateway_server.waiting_queue.application.dto.RemoveAllowedUserResult;
import com.tickatch.gateway_server.waiting_queue.application.dto.RemoveExpiredUsersResult;
import reactor.core.publisher.Mono;

public interface QueueRepository {

  Mono<String> lineUp(String userId);

  Mono<QueueStatusResponse> getCurrentStatus(String userId);

  Mono<Boolean> isAlreadyAllowedIn(String userId);

  Mono<RemoveAllowedUserResult> removeAllowedToken(String userId);

  Mono<Void> refreshAllowedInTimestamp(String userId);

  Mono<RemoveExpiredUsersResult> cleanupExpiredTokens();

  Mono<Boolean> removeWaitingToken(String userId);
}
