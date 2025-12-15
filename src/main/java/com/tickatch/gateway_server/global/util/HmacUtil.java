package com.tickatch.gateway_server.global.util;

import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HmacUtil {

  private static final String ALG = "HmacSHA256";

  public static String hmacSha26(String secret, String data) {
    try {
      SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), ALG);
      Mac mac = Mac.getInstance(ALG);
      mac.init(secretKey);

      byte[] rawHmac = mac.doFinal(data.getBytes());
      return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
    } catch (Exception e) {
      log.error("해싱을 실패했습니다. 데이터={}", data, e);
      throw new RuntimeException("해싱을 실패했습니다", e);
    }
  }
}
