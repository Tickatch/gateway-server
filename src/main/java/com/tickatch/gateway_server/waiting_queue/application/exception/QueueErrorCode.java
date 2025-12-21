package com.tickatch.gateway_server.waiting_queue.application.exception;

import com.tickatch.gateway_server.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum QueueErrorCode implements ErrorCode {

  USER_ID_NOT_FOUND(HttpStatus.NOT_FOUND.value(), "USER_ID_NOT_FOUND");

  private final int status;
  private final String code;
}
