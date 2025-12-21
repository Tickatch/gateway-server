package com.tickatch.gateway_server.waiting_queue.application.dto;

import lombok.Getter;

@Getter
public class QueueStatusChangeEvent implements QueueEvent {
  private final QueueStatusResponse statusResponse;
  private final long timestamp;

  public QueueStatusChangeEvent(QueueStatusResponse statusResponse) {
    this.statusResponse = statusResponse;
    this.timestamp = System.currentTimeMillis();
  }
}
