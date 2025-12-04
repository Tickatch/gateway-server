package com.tickatch.gateway_server.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import lombok.Getter;

/**
 * 필드별 검증 오류 정보.
 *
 * <p>검증 실패 시 어떤 필드에서 어떤 값으로 인해 오류가 발생했는지 상세 정보를 제공한다.
 *
 * <p>사용 예시:
 * <pre>{@code
 * // 필드 오류 생성
 * FieldError error = FieldError.of("email", "invalid-email", "이메일 형식이 올바르지 않습니다.");
 *
 * // 전역 오류 생성
 * FieldError global = FieldError.global("요청 데이터가 올바르지 않습니다.");
 * }</pre>
 *
 * <p>JSON 출력:
 * <pre>{@code
 * {
 *   "field": "email",
 *   "value": "invalid-email",
 *   "reason": "이메일 형식이 올바르지 않습니다."
 * }
 * }</pre>
 *
 * @author Tickatch
 * @since 0.0.1
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldError implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 오류가 발생한 필드명.
     * 전역 오류의 경우 "global"
     */
    private final String field;

    /**
     * 사용자가 입력한 잘못된 값.
     * 전역 오류의 경우 null
     */
    private final Object value;

    /**
     * 오류 발생 이유
     */
    private final String reason;

    private FieldError(String field, Object value, String reason) {
        this.field = field;
        this.value = value;
        this.reason = reason;
    }

    /**
     * 필드 오류 생성
     */
    public static FieldError of(String field, Object value, String reason) {
        return new FieldError(field, value, reason);
    }

    /**
     * 값 없이 필드 오류 생성
     */
    public static FieldError of(String field, String reason) {
        return new FieldError(field, null, reason);
    }

    /**
     * 전역 오류 생성 (특정 필드에 속하지 않는 오류)
     */
    public static FieldError global(String reason) {
        return new FieldError("global", null, reason);
    }

    /**
     * Spring FieldError에서 변환
     */
    public static FieldError from(org.springframework.validation.FieldError fieldError) {
        return new FieldError(
                fieldError.getField(),
                fieldError.getRejectedValue(),
                fieldError.getDefaultMessage()
        );
    }
}