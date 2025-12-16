package com.tickatch.gateway_server.global.api;

import com.tickatch.gateway_server.global.util.JsonUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class MonoResponseHelper {

  /**
   * 성공 응답
   */
  public <T> Mono<Void> writeSuccessWithStatus(ServerWebExchange exchange,
      HttpStatus status, T data, String message) {

    ApiResponse<T> response = ApiResponse.success(data, message);
    return writeResponse(exchange, status, response);
  }

  /**
   * 에러 응답 작성
   */
  public <T> Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status,
      String code, String message) {

    String path = exchange.getRequest().getPath().value();
    ApiResponse<T> response = ApiResponse.error(code, message, status.value(), path);

    return writeResponse(exchange, status, response);
  }

  /**
   * 응답 작성 (공통 로직)
   */
  private <T> Mono<Void> writeResponse(ServerWebExchange exchange, HttpStatus status, ApiResponse<T> response) {

    // 응답이 이미 커밋되었다면 처리하지 않음
    if (exchange.getResponse().isCommitted()) {
      return Mono.empty();
    }

    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

    try {
      byte[] bytes = JsonUtils.toBytes(response);
      DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
      return exchange.getResponse().writeWith(Mono.just(buffer));
    } catch (Exception e) {
      return Mono.error(e);
    }
  }

}
