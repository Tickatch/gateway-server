package com.tickatch.gateway_server.waiting_queue.application.dto;

public record LineUpResult(
    String queueToken,
    String msg
) {

}
