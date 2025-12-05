package com.tickatch.gateway_server.waiting_queue.domain;

import com.tickatch.gateway_server.waiting_queue.domain.dto.QueueStatus;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Queue {

  // 전역 대기번호 카운터
  private final LongAdder sequence = new LongAdder();

  // 입장 가능한 순번
  private final AtomicLong allowedToEnterSeq;

  // <대기하는 사용자, 대기번호>
  private final ConcurrentHashMap<String, Long> waitingUsers = new ConcurrentHashMap<>();

  public Queue(@Value("${queue.max-capacity}") int maxCapacity) {
    this.allowedToEnterSeq = new AtomicLong(maxCapacity);
  }

  // 대기하기
  public long lineUp(String userId) {
    // 처음 대기하는 사람
    if (!waitingUsers.containsKey(userId)) {
      sequence.increment();
      long userSeq = sequence.sum();
      waitingUsers.put(userId, userSeq);
    }

    // 이미 대기한 사람 (0 또는 음수는 입장 가능)
    return waitingUsers.get(userId) - allowedToEnterSeq.get();
  }

  // 입장 가능 여부 반환
  public boolean canEnter(String userId) {
    if (waitingUsers.containsKey(userId)) {
      return waitingUsers.get(userId) <= allowedToEnterSeq.get();
    }

    return false;
  }

  // 대기 상태 반환
  public QueueStatus getStatus(String userId) {
    if (waitingUsers.containsKey(userId)) {
      long lastEnteredPos = allowedToEnterSeq.get();
      long queueEndPos = sequence.sum();
      long absoluteUserPos = waitingUsers.get(userId);

      long queueSize = queueEndPos - lastEnteredPos;
      long relativeUserPos = absoluteUserPos - lastEnteredPos;
      long usersBehind = queueEndPos - absoluteUserPos;
      return new QueueStatus(queueSize, relativeUserPos, usersBehind);
    }

    throw new IllegalStateException();
  }

  // 나중에 예매/결제 서비스에서 하나의 트랜잭션이 끝나면 이벤트를 발행
  // 게이트웨이가 해당 이벤트를 구독하고 이벤트가 들어올 때마다 이 메소드를 호출해서 입장 인원 수를 하나씩 증가
  public void admitNextPerson() {
    if (allowedToEnterSeq.get() < sequence.sum()) {
      allowedToEnterSeq.incrementAndGet();
    }
  }
}
