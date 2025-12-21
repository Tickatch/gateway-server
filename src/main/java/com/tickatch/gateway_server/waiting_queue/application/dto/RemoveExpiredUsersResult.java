package com.tickatch.gateway_server.waiting_queue.application.dto;

import java.util.List;

public record RemoveExpiredUsersResult(List<String> userIds) {

}
