package com.tickatch.gateway_server.global.message;

/**
 * 메시지 코드와 인자를 기반으로 실제 출력할 메시지를 생성하는 인터페이스.
 *
 * <p>예외 처리 또는 응답 메시지 변환 시 사용되며,
 * 국제화(i18n) 또는 커스텀 메시지 전략을 적용하는 역할을 담당한다.
 *
 * <p>사용 예시:
 * <pre>{@code
 * // messages.properties
 * // TICKET_NOT_FOUND=티켓 {0}을(를) 찾을 수 없습니다.
 * // SEAT_RESERVED={0}구역 {1}번 좌석은 이미 예약되었습니다.
 *
 * String msg1 = messageResolver.resolve("TICKET_NOT_FOUND", 123L);
 * // → "티켓 123을(를) 찾을 수 없습니다."
 *
 * String msg2 = messageResolver.resolve("SEAT_RESERVED", "A", "15");
 * // → "A구역 15번 좌석은 이미 예약되었습니다."
 * }</pre>
 *
 * @author Tickatch
 * @since 0.0.1
 */
public interface MessageResolver {

    /**
     * 주어진 메시지 코드와 인자를 기반으로 메시지를 생성한다.
     *
     * @param code 메시지 코드 (예: "TICKET_NOT_FOUND")
     * @param args 메시지 포맷팅에 사용될 인자 목록
     * @return 생성된 메시지 문자열
     */
    String resolve(String code, Object... args);
}