# Gateway Server

Tickatch MSA의 **API Gateway** 서버입니다.

---

## 역할

- 모든 API 요청의 단일 진입점
- 라우팅 및 로드밸런싱
- **인증/인가 처리**
- **예매에 대한 대기열과 입장 허용 인원 관리**
  ![waiting queue pic](https://github.com/user-attachments/assets/bb04e20c-3bc5-4496-8335-e1679582e248)
- 이중화 구성으로 고가용성 확보

---

## 기술 스택

| 항목                         | 버전              |
|----------------------------|-----------------|
| Java                       | 21              |
| Spring Boot                | 4.0.0           |
| Spring Cloud               | 2025.1.0        |
| Spring Cloud Gateway       | 5.0.x (WebFlux) |
| Spring Security            | 7.0.x           |
| Spring Data Redis Reactive | 4.0.0           |
| Shedlock                   | 5.13.0          |

---

## 실행 방법

### 로컬 실행

```bash
./gradlew bootRun
```

### Docker 빌드

```bash
./gradlew clean bootJar
docker build -t ghcr.io/tickatch/gateway-server:latest .
```

---

## 이중화 구성

```
                    ┌─────────┐
                    │  Nginx  │
                    │   :80   │
                    └────┬────┘
                         │
           ┌─────────────┴─────────────┐
           ▼                           ▼
   ┌───────────────┐           ┌───────────────┐
   │ gateway-server│           │ gateway-server│
   │   -1 :8080    │           │   -2 :8081    │
   └───────────────┘           └───────────────┘
           │                           │
           └─────────────┬─────────────┘
                         │
                         ▼
              ┌─────────────────────┐
              │   Microservices     │
              │ (Account, User, ..) │
              └─────────────────────┘
```

Nginx가 두 Gateway 서버로 로드밸런싱합니다.

---

## 포트

| 인스턴스 | 포트 |
|----------|------|
| gateway-server-1 | 8080 |
| gateway-server-2 | 8081 |

---

## 환경 변수

| 변수                                     | 기본값                                         | 설명                      |
|----------------------------------------|---------------------------------------------|-------------------------|
| `SERVER_PORT`                          | 8080                                        | 서버 포트                   |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | http://localhost:8761/eureka/               | Eureka 서버 주소            |
| `ZIPKIN_ENDPOINT`                      | http://localhost:9411/api/v2/spans          | Zipkin 엔드포인트            |
| `ALLOWED_IN_MAX_CAPACITY`              | 100                                         | 입장을 허용할 수 있는 최대치        |
| `ALLOWED_IN_DURATION_SECONDS`          | 240                                            | 입장 허용 시간   |
| `JWT_JWKS_URI`                         | http://localhost:8090/.well-known/jwks.json | 인증 서버의 JWKS를 받을 수 있는 주소 |

---

## 라우팅 설정

라우팅 설정은 **config-repo**에서 관리됩니다.

| 경로                             | 서비스                      |
|--------------------------------|--------------------------|
| `/api/v1/auth/**`              | auth-service             |
| `/.well-known/**`              | auth-service-jwks        |
| `/api/v1/user/**`              | user-service             |
| `/api/v1/products/**`          | product-service          |
| `/api/v1/payments/**`          | payment-service          |
| `/api/v1/reservations/**`      | reservation-service      |
| `/api/v1/reservation-seats/**` | reservation-seat-service |
| `/api/v1/arthalls/**`          | arthall-service          |
| `/api/v1/tickets/**`           | ticket-service           |

각 서비스마다 `/{도메인-service}/v3/api-docs/**` 스웨거 문서 경로 라우팅도 지원. 

예) `/auth-service/v3/api-docs/**`

---

## API 엔드포인트

| 엔드포인트                            | 메소드    | 인증    | 설명                                       |
|----------------------------------|--------|-------|------------------------------------------|
| `/api/v1/queue/lineup`           | POST   | 🔐 필요 | 사용자를 대기열에 등록                             |
| `/api/v1/queue/stream` ✅         | GET    | 🔐 필요 | SSE 기반 대기열 상태 실시간 스트림 (상태 변경, 입장 허용 이벤트) |
| `/api/v1/queue/status` ⚠️        | GET    | 🔐 필요 | 사용자 대기열 상태 조회 (구버전: Polling 기반 상태 조회)    |
| `/api/v1/queue/allowed-in-token` | DELETE | 🔐 필요 | 입장 허용 토큰(사용자) 무효화                        |
| `/api/v1/queue/waiting-token`    | DELETE | 🔐 필요 | 대기열 토큰(사용자) 무효화                          |
| `/actuator/health`               | GET    | ❌ 불필요 | 헬스 체크                                    |
| `/actuator/gateway/routes`       | GET    | ❌ 불필요 | Gateway에 등록된 라우팅 목록 조회                   |
| `/actuator/prometheus`           | GET    | ❌ 불필요 | Prometheus 수집용 메트릭 제공                    |



---

## 디렉토리 구조

```
gateway_server
├── GatewayServerApplication.java
├── waiting_queue
│   ├── application
│   │   ├── QueueStatusNotifier.java
│   │   ├── WaitingQueueService.java
│   │   ├── dto
│   │   │   ├── AllowedInNotificationEvent.java
│   │   │   ├── QueueEvent.java
│   │   │   ├── QueueStatusChangeEvent.java
│   │   │   ├── QueueStatusResponse.java
│   │   │   ├── RemoveAllowedUserResult.java
│   │   │   └── RemoveExpiredUsersResult.java
│   │   ├── exception
│   │   │   ├── QueueErrorCode.java
│   │   │   └── QueueException.java
│   │   └── port
│   │       └── QueueRepository.java
│   ├── infrastructure
│   │   ├── config
│   │   │   ├── RedisLuaScriptConfig.java
│   │   │   ├── ShedLockConfig.java
│   │   │   └── SwaggerConfig.java
│   │   ├── filter
│   │   │   └── QueueFilter.java
│   │   ├── redis
│   │   │   └── RedisQueueRepositoryImpl.java
│   │   ├── scheduler
│   │   │   └── QueueScheduler.java
│   │   └── security
│   │       ├── AuthenticationEntryPoint.java
│   │       └── SecurityConfig.java
│   └── presentation
│       ├── dto
│       │   ├── AllowedInEvent.java
│       │   ├── ErrorEvent.java
│       │   └── HeartbeatEvent.java
│       └── webapi
│           ├── QueueApi.java
│           └── QueueSseController.java
├── global
│   ├── api
│   │   ├── ApiResponse.java
│   │   └── MonoResponseHelper.java
│   ├── error
│   │   ├── BusinessException.java
│   │   ├── ErrorCode.java
│   │   ├── FieldError.java
│   │   ├── GlobalErrorCode.java
│   │   └── GlobalExceptionHandler.java
│   ├── message
│   │   ├── DefaultMessageResolver.java
│   │   └── MessageResolver.java
│   └── util
│       ├── HmacUtil.java
│       └── JsonUtils.java
└── security
    └── JwtAuthenticationFilter.java

```

---

## Security 설정

인증, 상품 조회, 아트홀 조회, 티켓 사용(MVP로 열어 둠) 경로들 빼고는 다 인증이 필요합니다.

```java
@Bean
public SecurityWebFilterChain securityWebFilterChain(
    ServerHttpSecurity http,
    JwtAuthenticationFilter jwtAuthenticationFilter,
    AuthenticationEntryPoint authenticationEntryPoint
) {
  return http
      .csrf(ServerHttpSecurity.CsrfSpec::disable)
      .authorizeExchange(exchanges -> exchanges
          // Actuator 엔드포인트 허용
          .pathMatchers("/actuator/**").permitAll()
          // Health Check
          .pathMatchers("/health/**").permitAll()
          // Swagger / OpenAPI
          .pathMatchers("/swagger-ui/**").permitAll()
          .pathMatchers("/swagger-ui.html").permitAll()
          .pathMatchers("/v3/api-docs/**").permitAll()
          // 각 서비스별 API docs
          .pathMatchers("/auth-service/v3/api-docs/**").permitAll()
          .pathMatchers("/product-service/v3/api-docs/**").permitAll()
          .pathMatchers("/reservation-service/v3/api-docs/**").permitAll()
          .pathMatchers("/reservation-seat-service/v3/api-docs/**").permitAll()
          .pathMatchers("/arthall-service/v3/api-docs/**").permitAll()
          .pathMatchers("/ticket-service/v3/api-docs/**").permitAll()

          // ========================================
          // Auth Service - 공개 API
          // ========================================
          .pathMatchers("/api/v1/auth/login").permitAll()
          .pathMatchers("/api/v1/auth/register").permitAll()
          .pathMatchers("/api/v1/auth/refresh").permitAll()
          .pathMatchers("/api/v1/auth/check-email").permitAll()

          .pathMatchers(HttpMethod.GET, "/api/v1/products").permitAll()
          .pathMatchers(HttpMethod.GET, "/api/v1/products/*").permitAll()
          .pathMatchers(HttpMethod.GET, "/api/v1/arthalls/**").permitAll()
          .pathMatchers(HttpMethod.POST, "/api/v1/tickets/*/use").permitAll()

          // OAuth - 로그인/콜백만 공개 (link, unlink는 인증 필요)
          .pathMatchers(HttpMethod.GET, "/api/v1/auth/oauth/*/callback").permitAll()
          .pathMatchers(HttpMethod.GET, "/api/v1/auth/oauth/*").permitAll()

          .anyExchange().authenticated()
      )
      .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
      .addFilterAfter(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
      .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
      .build();
}
```

### JWT 인증

| 구성 요소 | 설명                                        |
|----------|-------------------------------------------|
| `JwtAuthenticationFilter` | JWT 토큰 검증 필터                              |
| 인증 헤더 주입 | `X-User-Id`, `X-User-Type` 헤더로 내부 서비스에 전달 |

**인증 흐름**
```
Client → Gateway → JWT 검증 → 헤더 주입 → 내부 서비스
```

---

## 의존 관계

```
Eureka Server (8761, 8762)
        ▲
        │
Config Server (8888)
        ▲
        │
Gateway Server (8080, 8081)
```

Gateway Server는 Config Server와 Eureka Server가 먼저 기동되어야 합니다.