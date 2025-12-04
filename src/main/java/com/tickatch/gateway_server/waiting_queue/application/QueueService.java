package com.tickatch.gateway_server.waiting_queue.application;

import com.tickatch.gateway_server.waiting_queue.application.dto.QueueStatusResponse;
import com.tickatch.gateway_server.waiting_queue.domain.Queue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueueService {

  private final Queue queue;

  public long lineUp(String userId) {
    return queue.lineUp(userId);
  }

  public boolean canEnter(String userId) {
    return queue.canEnter(userId);
  }

  public QueueStatusResponse getStatus(String userId) {
    return QueueStatusResponse.from(queue.getStatus(userId));
  }

  public void admitNextPerson() {
    queue.admitNextPerson();
  }
}
