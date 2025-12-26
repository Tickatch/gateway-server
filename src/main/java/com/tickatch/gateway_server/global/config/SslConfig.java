package com.tickatch.gateway_server.global.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.http.client.HttpClient;

@Configuration
public class SslConfig {

  @Bean
  public HttpClient httpClient() throws Exception {
    SslContext sslContext = SslContextBuilder.forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .build();

    return HttpClient.create()
        .secure(spec -> spec.sslContext(sslContext));
  }
}