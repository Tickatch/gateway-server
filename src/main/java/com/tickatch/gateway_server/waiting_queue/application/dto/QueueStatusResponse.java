package com.tickatch.gateway_server.waiting_queue.application.dto;

import com.tickatch.gateway_server.waiting_queue.domain.dto.QueueStatus;

public record QueueStatusResponse(
    Long totalQueueSize,
    Long userQueuePosition,
    Long usersBehind
) {

  public static QueueStatusResponse from(QueueStatus queueStatus) {
    return new QueueStatusResponse(queueStatus.totalQueueSize(), queueStatus.userQueuePosition(),
        queueStatus.usersBehind());
  }
}
