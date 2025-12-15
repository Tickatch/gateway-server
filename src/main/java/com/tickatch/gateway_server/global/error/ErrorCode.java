package com.tickatch.gateway_server.global.error;

/**
 * 에러 코드 인터페이스.
 *
 * <p>모든 도메인별 에러 코드 Enum이 구현해야 하는 인터페이스.
 * 메시지는 {@link io.github.tickatch.common.message.MessageResolver}를 통해 별도 관리된다.
 *
 * <p>사용 예시:
 * <pre>{@code
 * @Getter
 * @RequiredArgsConstructor
 * public enum TicketErrorCode implements ErrorCode {
 *     TICKET_NOT_FOUND(404, "TICKET_NOT_FOUND"),
 *     TICKET_SOLD_OUT(409, "TICKET_SOLD_OUT"),
 *     SEAT_ALREADY_RESERVED(409, "SEAT_ALREADY_RESERVED");
 *
 *     private final int status;
 *     private final String code;
 * }
 * }</pre>
 *
 * @author Tickatch
 * @since 0.0.1
 */
public interface ErrorCode {

    /**
     * HTTP 상태 코드 반환
     *
     * @return HTTP 상태 코드 (예: 400, 404, 500)
     */
    int getStatus();

    /**
     * 에러 식별 코드 반환.
     * 이 코드는 메시지 리소스 파일(messages.properties)의 키로 사용된다.
     *
     * @return 에러 코드 문자열 (예: "TICKET_NOT_FOUND")
     */
    String getCode();
}