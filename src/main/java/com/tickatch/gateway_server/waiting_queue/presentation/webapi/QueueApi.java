package com.tickatch.gateway_server.waiting_queue.presentation.webapi;

import com.tickatch.gateway_server.global.api.ApiResponse;
import com.tickatch.gateway_server.waiting_queue.application.WaitingQueueService;
import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusResponse;
import com.tickatch.gateway_server.waiting_queue.application.dto.LineUpResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueApi {

  private final WaitingQueueService queueService;

  @PostMapping("/lineup")
  public Mono<ApiResponse<LineUpResponse>> lineUp(@RequestHeader("X-Queue-User-Id") String queueUserId) {
    return queueService.lineUp(queueUserId).map(res -> ApiResponse.success(res, "대기열 등록 성공"));
  }

  @GetMapping("/status")
  public Mono<ApiResponse<QueueStatusResponse>> status(
      @RequestHeader("X-Queue-User-Id") String queueUserId,
      @RequestHeader("X-Queue-Token") String queueToken,
      @RequestHeader("X-Queue-Timestamp") String queueTimestamp
  ) {
    return queueService.canEnter(queueToken, queueUserId, queueTimestamp)
        .flatMap(canEnter -> {
          if (canEnter) {
            return Mono.just(ApiResponse.success(null, "입장 가능합니다."));
          } else {
            return queueService.getStatus(queueToken)
                .flatMap(status -> Mono.just(ApiResponse.success(status)));
          }
        });
  }
}
