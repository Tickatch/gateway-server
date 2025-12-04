package com.tickatch.gateway_server.global.error;


import com.tickatch.gateway_server.global.api.ApiResponse;
import com.tickatch.gateway_server.global.message.MessageResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 전역 예외 핸들러.
 *
 * <p>모든 예외를 일관된 형식으로 처리하며, {@link MessageResolver}를 통해 동적 메시지를 생성한다.
 *
 * <p>처리하는 예외 유형:
 * <ul>
 *   <li><b>비즈니스 예외</b> - {@link BusinessException}</li>
 *   <li><b>검증 예외</b> - {@link MethodArgumentNotValidException}, {@link BindException},
 *       {@link HandlerMethodValidationException} (Spring 6+)</li>
 *   <li><b>요청 예외</b> - JSON 파싱 실패, 파라미터 누락, 타입 불일치 등</li>
 *   <li><b>인증/인가 예외</b> - {@link AccessDeniedException}</li>
 *   <li><b>리소스 예외</b> - 엔드포인트 없음, 리소스 없음</li>
 *   <li><b>기타 예외</b> - 미처리 예외는 500 에러로 처리</li>
 * </ul>
 *
 * <p>각 서비스에서 이 핸들러를 상속하여 도메인별 예외 처리 추가 가능:
 * <pre>{@code
 * @RestControllerAdvice
 * public class TicketExceptionHandler extends GlobalExceptionHandler {
 *
 *     public TicketExceptionHandler(MessageResolver messageResolver) {
 *         super(messageResolver);
 *     }
 *
 *     @ExceptionHandler(TicketNotFoundException.class)
 *     public ResponseEntity<ApiResponse<Void>> handleTicketNotFound(
 *             HttpServletRequest request, TicketNotFoundException e) {
 *         // 커스텀 처리
 *     }
 * }
 * }</pre>
 *
 * @author Tickatch
 * @since 0.0.1
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageResolver messageResolver;

    // ========================================
    // 비즈니스 예외 처리
    // ========================================

    @ExceptionHandler(BusinessException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleBusinessException(
            ServerWebExchange exchange,
            BusinessException e) {

        String path = exchange.getRequest().getPath().value();
        String code = e.getCode();
        String message = messageResolver.resolve(code, e.getErrorArgs());

        log.warn("비즈니스 예외: {} - {} (path: {})", code, message, path);

        ApiResponse<Void> response = ApiResponse.error(
                code,
                message,
                e.getStatus(),
                path
        );

        return Mono.just(ResponseEntity.status(e.getStatus()).body(response));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleAllExceptions(
            ServerWebExchange exchange,
            Exception e) {

        String path = exchange.getRequest().getPath().value();
        String code = GlobalErrorCode.INTERNAL_SERVER_ERROR.getCode();
        String message = messageResolver.resolve(code);

        log.error("처리되지 않은 예외 (path: {}): ", path, e);

        ApiResponse<Void> response = ApiResponse.error(
                code,
                message,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                path
        );

        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response));
    }
}