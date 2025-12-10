package com.tickatch.gateway_server.waiting_queue.application.exception;

import com.tickatch.gateway_server.global.error.BusinessException;
import com.tickatch.gateway_server.global.error.ErrorCode;

public class QueueException extends BusinessException {

  public QueueException(ErrorCode errorCode) {
    super(errorCode);
  }
}
