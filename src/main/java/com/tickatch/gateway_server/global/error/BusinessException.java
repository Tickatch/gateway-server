package com.tickatch.gateway_server.global.error;

import lombok.Getter;

/**
 * 비즈니스 예외 기본 클래스.
 *
 * <p>모든 비즈니스 로직 예외의 부모 클래스.
 * {@link ErrorCode}를 통해 에러 코드와 HTTP 상태를 포함하고,
 * errorArgs를 통해 동적 메시지 생성을 지원한다.
 *
 * <p>사용 예시:
 * <pre>{@code
 * // 기본 사용 (메시지 인자 없음)
 * throw new BusinessException(GlobalErrorCode.NOT_FOUND);
 *
 * // 메시지 인자 포함 (messages.properties: TICKET_NOT_FOUND=티켓 {0}을(를) 찾을 수 없습니다.)
 * throw new BusinessException(TicketErrorCode.TICKET_NOT_FOUND, ticketId);
 * // → "티켓 123을(를) 찾을 수 없습니다."
 *
 * // 다중 인자 (SEAT_RESERVED={0}구역 {1}번 좌석은 이미 예약되었습니다.)
 * throw new BusinessException(TicketErrorCode.SEAT_RESERVED, "A", "15");
 * // → "A구역 15번 좌석은 이미 예약되었습니다."
 *
 * // 원인 예외 포함
 * throw new BusinessException(GlobalErrorCode.DATABASE_ERROR, e, "users");
 * }</pre>
 *
 * @author Tickatch
 * @since 0.0.1
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 메시지 포맷에 사용될 인자 목록.
     * messages.properties의 {0}, {1} 등을 치환하는 데 사용된다.
     */
    private final Object[] errorArgs;

    /**
     * ErrorCode만으로 예외 생성
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getCode());
        this.errorCode = errorCode;
        this.errorArgs = new Object[0];
    }

    /**
     * ErrorCode와 메시지 인자로 예외 생성
     *
     * @param errorCode 에러 코드
     * @param errorArgs 메시지 포맷 인자 ({0}, {1} 치환용)
     */
    public BusinessException(ErrorCode errorCode, Object... errorArgs) {
        super(errorCode.getCode());
        this.errorCode = errorCode;
        this.errorArgs = errorArgs != null ? errorArgs : new Object[0];
    }

    /**
     * ErrorCode, 원인 예외, 메시지 인자로 예외 생성
     *
     * @param errorCode 에러 코드
     * @param cause     원인 예외
     * @param errorArgs 메시지 포맷 인자
     */
    public BusinessException(ErrorCode errorCode, Throwable cause, Object... errorArgs) {
        super(errorCode.getCode(), cause);
        this.errorCode = errorCode;
        this.errorArgs = errorArgs != null ? errorArgs : new Object[0];
    }

    /**
     * HTTP 상태 코드 반환
     */
    public int getStatus() {
        return errorCode.getStatus();
    }

    /**
     * 에러 코드 문자열 반환
     */
    public String getCode() {
        return errorCode.getCode();
    }
}