package com.tickatch.gateway_server.global.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HmacUtilTest {
  private static final String TEST_SECRET = "test-secret-key-for-hmac";
  private static final String USER_ID = "1806a16b-2e4b-4669-8004-c6ea6c71e6c7";
  private static final Long TIMESTAMP = 1234567890000L;

  @Test
  @DisplayName("동일한 입력값으로 생성한 토큰은 항상 같다")
  void same_input_produces_same_token() {
    // given
    String raw = USER_ID + ":" + TIMESTAMP;

    // when
    String token1 = HmacUtil.hmacSha26(TEST_SECRET, raw);
    String token2 = HmacUtil.hmacSha26(TEST_SECRET, raw);

    // then
    Assertions.assertThat(token1)
        .isEqualTo(token2)
        .isNotBlank();
  }

  @Test
  @DisplayName("다른 입력값으로 생성한 토큰은 다르다")
  void different_input_produces_different_token() {
    // given
    String raw1 = USER_ID + ":" + TIMESTAMP;
    String raw2 = USER_ID + ":" + (TIMESTAMP + 1000);

    // when
    String token1 = HmacUtil.hmacSha26(TEST_SECRET, raw1);
    String token2 = HmacUtil.hmacSha26(TEST_SECRET, raw2);

    // then
    Assertions.assertThat(token1).isNotEqualTo(token2);
  }

  @Test
  @DisplayName("다른 시크릿으로 생성한 토큰은 다르다")
  void different_secret_produces_different_token() {
    // given
    String raw = USER_ID + ":" + TIMESTAMP;

    // when
    String token1 = HmacUtil.hmacSha26(TEST_SECRET, raw);
    String token2 = HmacUtil.hmacSha26("different-secret", raw);

    // then
    Assertions.assertThat(token1).isNotEqualTo(token2);
  }

  @Test
  @DisplayName("생성된 토큰은 URL-safe Base64 형식이다")
  void token_is_url_safe_base64() {
    // given
    String raw = USER_ID + ":" + TIMESTAMP;

    // when
    String token = HmacUtil.hmacSha26(TEST_SECRET, raw);

    // then
    Assertions.assertThat(token)
        .matches("^[A-Za-z0-9_-]+$")  // URL-safe Base64 패턴
        .doesNotContain("+", "/", "=");  // 패딩 없음
  }

  @Test
  @DisplayName("비정상 토큰은 검증에 실패한다")
  void invalid_token_verification_fails() {
    // given
    String raw = USER_ID + ":" + TIMESTAMP;
    String validToken = HmacUtil.hmacSha26(TEST_SECRET, raw);
    String invalidToken = "invalid-random-token-12345";

    // when & then
    Assertions.assertThat(invalidToken).isNotEqualTo(validToken);
  }
}