package com.tickatch.gateway_server.waiting_queue.application.dto;

public record QueueStatusResponse(
    // 총 대기 인원
    Long totalQueueSize,
    // 사용자 대기번호
    Long userQueuePosition,
    // 사용자 기준 뒤로 몇 명
    Long usersBehind
) {
}
