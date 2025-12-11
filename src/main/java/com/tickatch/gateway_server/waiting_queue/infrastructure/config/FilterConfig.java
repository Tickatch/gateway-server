package com.tickatch.gateway_server.waiting_queue.infrastructure.config;

import com.tickatch.gateway_server.global.api.MonoResponseHelper;
import com.tickatch.gateway_server.security.JwtAuthenticationFilter;
import com.tickatch.gateway_server.waiting_queue.application.WaitingQueueService;
import com.tickatch.gateway_server.waiting_queue.infrastructure.filter.QueueFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {
  @Bean
  public JwtAuthenticationFilter jwtAuthenticationFilter() {
    return new JwtAuthenticationFilter();
  }

  @Bean
  public QueueFilter queueFilter(
      WaitingQueueService queueService,
      MonoResponseHelper monoResponseHelper) {
    return new QueueFilter(queueService, monoResponseHelper);
  }
}
