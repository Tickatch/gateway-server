package com.tickatch.gateway_server.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 전역 공통 에러 코드.
 *
 * <p>도메인에 종속되지 않는 전역 오류에 대해 일관된 HTTP 상태와 에러 식별자를 제공한다.
 * 메시지는 messages.properties에서 코드를 키로 사용하여 관리된다.
 *
 * @author Tickatch
 * @since 0.0.1
 */
@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements ErrorCode {

    // ========================================
    // 클라이언트 에러 - 요청 (400번대)
    // ========================================

    /** 잘못된 요청 */
    BAD_REQUEST(400, "BAD_REQUEST"),

    /** 입력값 검증 실패 */
    VALIDATION_ERROR(400, "VALIDATION_ERROR"),

    /** JSON 파싱 실패 */
    INVALID_JSON(400, "INVALID_JSON"),

    /** 타입 변환 실패 */
    TYPE_MISMATCH(400, "TYPE_MISMATCH"),

    /** 필수 파라미터 누락 */
    MISSING_PARAMETER(400, "MISSING_PARAMETER"),

    // ========================================
    // 인증/인가 에러 (401, 403)
    // ========================================

    /** 인증 필요 */
    UNAUTHORIZED(401, "UNAUTHORIZED"),

    /** 유효하지 않은 토큰 */
    INVALID_TOKEN(401, "INVALID_TOKEN"),

    /** 만료된 토큰 */
    EXPIRED_TOKEN(401, "EXPIRED_TOKEN"),

    /** 토큰 누락 */
    TOKEN_MISSING(401, "TOKEN_MISSING"),

    /** 인증 정보 불일치 */
    INVALID_CREDENTIALS(401, "INVALID_CREDENTIALS"),

    /** 접근 권한 없음 */
    FORBIDDEN(403, "FORBIDDEN"),

    /** 리소스 접근 거부 */
    ACCESS_DENIED(403, "ACCESS_DENIED"),

    // ========================================
    // 리소스 에러 (404, 405, 409, 415)
    // ========================================

    /** 리소스 없음 */
    NOT_FOUND(404, "NOT_FOUND"),

    /** 엔드포인트 없음 */
    ENDPOINT_NOT_FOUND(404, "ENDPOINT_NOT_FOUND"),

    /** 지원하지 않는 HTTP 메서드 */
    METHOD_NOT_ALLOWED(405, "METHOD_NOT_ALLOWED"),

    /** 리소스 충돌 */
    CONFLICT(409, "CONFLICT"),

    /** 중복 리소스 */
    DUPLICATE_RESOURCE(409, "DUPLICATE_RESOURCE"),

    /** 낙관적 락 실패 */
    OPTIMISTIC_LOCK_FAILURE(409, "OPTIMISTIC_LOCK_FAILURE"),

    /** 지원하지 않는 미디어 타입 */
    UNSUPPORTED_MEDIA_TYPE(415, "UNSUPPORTED_MEDIA_TYPE"),

    // ========================================
    // 비즈니스 로직 에러 (422)
    // ========================================

    /** 비즈니스 로직 오류 */
    BUSINESS_ERROR(422, "BUSINESS_ERROR"),

    /** 잘못된 상태 */
    INVALID_STATE(422, "INVALID_STATE"),

    /** 허용되지 않은 작업 */
    OPERATION_NOT_PERMITTED(422, "OPERATION_NOT_PERMITTED"),

    // ========================================
    // 서버 에러 (500번대)
    // ========================================

    /** 서버 내부 오류 */
    INTERNAL_SERVER_ERROR(500, "INTERNAL_SERVER_ERROR"),

    /** 데이터베이스 오류 */
    DATABASE_ERROR(500, "DATABASE_ERROR"),

    /** 외부 API 오류 */
    EXTERNAL_API_ERROR(502, "EXTERNAL_API_ERROR"),

    /** 서비스 이용 불가 */
    SERVICE_UNAVAILABLE(503, "SERVICE_UNAVAILABLE"),

    /** 서비스 간 통신 오류 */
    SERVICE_COMMUNICATION_ERROR(503, "SERVICE_COMMUNICATION_ERROR"),

    /** 서킷 브레이커 오픈 */
    CIRCUIT_BREAKER_OPEN(503, "CIRCUIT_BREAKER_OPEN"),

    /** 재시도 횟수 초과 */
    RETRY_EXHAUSTED(503, "RETRY_EXHAUSTED"),

    /** 외부 API 타임아웃 */
    EXTERNAL_API_TIMEOUT(504, "EXTERNAL_API_TIMEOUT");

    private final int status;
    private final String code;
}