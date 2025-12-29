package com.tickatch.gateway_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/*게이트웨이*/
@SpringBootApplication
@EnableScheduling
public class GatewayServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(GatewayServerApplication.class, args);
  }

}
