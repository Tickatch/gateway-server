package com.tickatch.gateway_server.waiting_queue.presentation.webapi;

import com.tickatch.gateway_server.global.api.ApiResponse;
import com.tickatch.gateway_server.waiting_queue.application.QueueService;
import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusResponse;
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

  private final QueueService queueService;

  @PostMapping("/lineup")
  public Mono<ApiResponse<Void>> lineUp(@RequestHeader("X-User-Id") String userId) {
    long userSeq = queueService.lineUp(userId);

    if (userSeq <= 0) {
      return Mono.just(ApiResponse.success(null, "입장 가능합니다."));
    }
    return Mono.just(ApiResponse.success(null, "대기번호 " + userSeq + "번 입니다."));
  }

  @GetMapping("/status")
  public Mono<ApiResponse<QueueStatusResponse>> status(@RequestHeader("X-User-Id") String userId) {
    QueueStatusResponse status = queueService.getStatus(userId);

    if (status.userQueuePosition() <= 0) {
      return Mono.just(ApiResponse.success(null, "입장 가능합니다."));
    }
    return Mono.just(ApiResponse.success(status));
  }
}
