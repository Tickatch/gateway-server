package com.tickatch.gateway_server.waiting_queue.infrastructure.scheduler;

import com.tickatch.gateway_server.waiting_queue.application.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QueueScheduler {

  private final WaitingQueueService queueService;

  @Scheduled(fixedRate = 5000)
  public void processNextEntry() {
//    queueService.admitNextPerson().subscribe();
  }
}
