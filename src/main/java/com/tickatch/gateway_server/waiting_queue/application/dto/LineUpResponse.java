package com.tickatch.gateway_server.waiting_queue.application.dto;

public record LineUpResponse(
    Long queueTimestamp,
    String queueToken
) {

}
