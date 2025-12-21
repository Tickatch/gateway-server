package com.tickatch.gateway_server.waiting_queue.infrastructure.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisLuaScriptConfig {
  @Bean
  public RedisScript<String> lineupScript() {
    return RedisScript.of(new ClassPathResource("lua/lineup.lua"), String.class);
  }

  @Bean
  public RedisScript<List> removeAllowedUserIdScript() {
    return RedisScript.of(new ClassPathResource("lua/remove-allowed-user-id.lua"), List.class);
  }

  @Bean
  public RedisScript<List> cleanupExpiredUserIdsScript() {
    return RedisScript.of(new ClassPathResource("lua/cleanup-expired-user-ids.lua"), List.class);
  }
}
