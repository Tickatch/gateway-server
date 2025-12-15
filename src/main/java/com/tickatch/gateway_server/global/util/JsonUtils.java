package com.tickatch.gateway_server.global.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON 유틸리티.
 *
 * <p>Jackson ObjectMapper를 사용한 JSON 직렬화/역직렬화
 *
 * @author Tickatch
 * @since 0.0.1
 */
@Slf4j
public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        OBJECT_MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        OBJECT_MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    private JsonUtils() {
        throw new AssertionError("유틸리티 클래스는 인스턴스화할 수 없습니다.");
    }

    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    // ========================================
    // Object → JSON 변환
    // ========================================

    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("객체를 JSON으로 변환 실패: {}", object, e);
            throw new JsonConversionException("JSON 직렬화에 실패했습니다.", e);
        }
    }

    public static Optional<String> toJsonSafe(Object object) {
        try {
            return Optional.of(OBJECT_MAPPER.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            log.warn("객체를 JSON으로 변환 실패: {}", object, e);
            return Optional.empty();
        }
    }

    public static String toPrettyJson(Object object) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("객체를 Pretty JSON으로 변환 실패: {}", object, e);
            throw new JsonConversionException("JSON 직렬화에 실패했습니다.", e);
        }
    }

    public static byte[] toBytes(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            log.error("객체를 bytes로 변환 실패: {}", object, e);
            throw new JsonConversionException("JSON 직렬화에 실패했습니다.", e);
        }
    }

    // ========================================
    // JSON → Object 변환
    // ========================================

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON을 객체로 변환 실패: {}", json, e);
            throw new JsonConversionException("JSON 역직렬화에 실패했습니다.", e);
        }
    }

    public static <T> Optional<T> fromJsonSafe(String json, Class<T> clazz) {
        try {
            return Optional.of(OBJECT_MAPPER.readValue(json, clazz));
        } catch (JsonProcessingException e) {
            log.warn("JSON을 객체로 변환 실패: {}", json, e);
            return Optional.empty();
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            log.error("JSON을 객체로 변환 실패: {}", json, e);
            throw new JsonConversionException("JSON 역직렬화에 실패했습니다.", e);
        }
    }

    public static <T> T fromBytes(byte[] bytes, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(bytes, clazz);
        } catch (Exception e) {
            log.error("bytes를 객체로 변환 실패", e);
            throw new JsonConversionException("JSON 역직렬화에 실패했습니다.", e);
        }
    }

    // ========================================
    // 편의 메서드
    // ========================================

    public static <T> List<T> fromJsonToList(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            log.error("JSON을 List로 변환 실패: {}", json, e);
            throw new JsonConversionException("JSON 역직렬화에 실패했습니다.", e);
        }
    }

    public static Map<String, Object> fromJsonToMap(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("JSON을 Map으로 변환 실패: {}", json, e);
            throw new JsonConversionException("JSON 역직렬화에 실패했습니다.", e);
        }
    }

    public static JsonNode parseJson(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            log.error("JSON 파싱 실패: {}", json, e);
            throw new JsonConversionException("JSON 파싱에 실패했습니다.", e);
        }
    }

    public static <T> T convert(Object source, Class<T> targetClass) {
        return OBJECT_MAPPER.convertValue(source, targetClass);
    }

    public static boolean isValidJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            OBJECT_MAPPER.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    // ========================================
    // 예외 클래스
    // ========================================

    public static class JsonConversionException extends RuntimeException {
        public JsonConversionException(String message) {
            super(message);
        }

        public JsonConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}