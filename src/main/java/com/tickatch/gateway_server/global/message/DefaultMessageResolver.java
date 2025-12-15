package com.tickatch.gateway_server.global.message;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Spring MessageSource 기반 MessageResolver 구현체.
 *
 * <p>messages.properties 파일에서 메시지를 조회하고,
 * 인자를 치환하여 최종 메시지를 생성한다.
 *
 * <p>메시지 파일 설정 예시 (application.yml):
 * <pre>{@code
 * spring:
 *   messages:
 *     basename: messages
 *     encoding: UTF-8
 * }</pre>
 *
 * <p>messages.properties 예시:
 * <pre>
 * # 전역 에러
 * BAD_REQUEST=잘못된 요청입니다.
 * VALIDATION_ERROR=입력값이 유효하지 않습니다.
 * NOT_FOUND={0}을(를) 찾을 수 없습니다.
 * UNAUTHORIZED=인증이 필요합니다.
 * FORBIDDEN=접근 권한이 없습니다.
 * INTERNAL_SERVER_ERROR=서버 오류가 발생했습니다.
 *
 * # 도메인별 에러
 * TICKET_NOT_FOUND=티켓 {0}을(를) 찾을 수 없습니다.
 * TICKET_SOLD_OUT={0} 공연의 티켓이 매진되었습니다.
 * SEAT_ALREADY_RESERVED={0}구역 {1}번 좌석은 이미 예약되었습니다.
 * </pre>
 *
 * <p>다국어 지원 (messages_en.properties):
 * <pre>
 * TICKET_NOT_FOUND=Ticket {0} not found.
 * SEAT_ALREADY_RESERVED=Seat {1} in section {0} is already reserved.
 * </pre>
 *
 * @author Tickatch
 * @since 0.0.1
 */
@Component
@RequiredArgsConstructor
public class DefaultMessageResolver implements MessageResolver {

    private final MessageSource messageSource;

    /**
     * 메시지 코드와 인자로 메시지를 생성한다.
     *
     * <p>현재 Locale에 맞는 메시지를 조회하고,
     * 메시지가 없으면 코드 자체를 반환한다.
     *
     * @param code 메시지 코드
     * @param args 메시지 포맷 인자
     * @return 생성된 메시지 (메시지가 없으면 코드 반환)
     */
    @Override
    public String resolve(String code, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(code, args, locale);
        } catch (NoSuchMessageException e) {
            // 메시지가 없으면 코드 자체를 반환
            return code;
        }
    }

    /**
     * 지정된 Locale로 메시지를 생성한다.
     *
     * @param code   메시지 코드
     * @param locale 로케일
     * @param args   메시지 포맷 인자
     * @return 생성된 메시지
     */
    public String resolve(String code, Locale locale, Object... args) {
        try {
            return messageSource.getMessage(code, args, locale);
        } catch (NoSuchMessageException e) {
            return code;
        }
    }

    /**
     * 기본 메시지와 함께 조회한다.
     * 메시지가 없으면 기본 메시지를 반환한다.
     *
     * @param code           메시지 코드
     * @param defaultMessage 기본 메시지
     * @param args           메시지 포맷 인자
     * @return 생성된 메시지 (없으면 기본 메시지)
     */
    public String resolveWithDefault(String code, String defaultMessage, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(code, args, defaultMessage, locale);
    }
}