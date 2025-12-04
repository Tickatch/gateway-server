package com.tickatch.gateway_server.waiting_queue.infrastructure.scheduler;

import com.tickatch.gateway_server.waiting_queue.application.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueueScheduler {

  private final QueueService queueService;

  @Scheduled(fixedRate = 5000)
  public void processNextEntry() {
    queueService.admitNextPerson();
  }
}
