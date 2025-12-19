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
  public RedisScript<List> removeAllowedTokenScript() {
    return RedisScript.of(new ClassPathResource("lua/remove-allowed-token.lua"), List.class);
  }

  @Bean
  public RedisScript<List> cleanupExpiredTokensScript() {
    return RedisScript.of(new ClassPathResource("lua/cleanup-expired-tokens.lua"), List.class);
  }
}
