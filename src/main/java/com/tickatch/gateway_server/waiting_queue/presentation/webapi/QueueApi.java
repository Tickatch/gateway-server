package com.tickatch.gateway_server.waiting_queue.presentation.webapi;

import com.tickatch.gateway_server.global.api.ApiResponse;
import com.tickatch.gateway_server.waiting_queue.application.WaitingQueueService;
import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueApi {

  private final WaitingQueueService queueService;

  @PostMapping("/lineup")
  public Mono<ApiResponse<Void>> lineUp(@AuthenticationPrincipal Jwt jwt) {
    String userId = jwt.getSubject();

    return queueService.lineUp(userId)
        .map(msg -> ApiResponse.success(null, msg));
  }

  @GetMapping("/status")
  public Mono<ApiResponse<QueueStatusResponse>> status(@AuthenticationPrincipal Jwt jwt) {
    String userId = jwt.getSubject();

    return queueService.canEnter(userId)
        .flatMap(canEnter -> {
          if (canEnter) {
            return Mono.just(ApiResponse.success(null, "입장 가능합니다."));
          } else {
            return queueService.getStatus(userId)
                .flatMap(status -> Mono.just(ApiResponse.success(status)));
          }
        });
  }

  @DeleteMapping("/allowed-in-token")
  public Mono<ApiResponse<Void>> removeAllowedInToken(@AuthenticationPrincipal Jwt jwt) {
    String userId = jwt.getSubject();

    return queueService.removeAllowedUserId(userId)
        .map(removed -> {
          if (removed) {
            return ApiResponse.success(null, "입장 토큰이 무효화되었습니다.");
          } else {
            return ApiResponse.error("NOT_FOUND", "무효화할 입장 토큰이 없습니다.", HttpStatus.NOT_FOUND.value());
          }
        });
  }

  @DeleteMapping("/waiting-token")
  public Mono<ApiResponse<Void>> removeWaitingToken(@AuthenticationPrincipal Jwt jwt) {
    String userId = jwt.getSubject();

    return queueService.removeWaitingUserId(userId)
        .map(removed -> {
          if (removed) {
            return ApiResponse.success(null, "대기열 토큰이 무효화되었습니다.");
          } else {
            return ApiResponse.error("NOT_FOUND", "무효화할 대기열 토큰이 없습니다.", HttpStatus.NOT_FOUND.value());
          }
        });
  }
}
