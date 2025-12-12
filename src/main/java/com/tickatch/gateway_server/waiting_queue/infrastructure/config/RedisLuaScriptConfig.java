package com.tickatch.gateway_server.waiting_queue.infrastructure.config;

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
}
