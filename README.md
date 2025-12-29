# Gateway Server

대규모 예매 트래픽 상황에서 백엔드 시스템을 보호하기 위해 대기열과 입장 제어를 담당하는 **API Gateway** 서버입니다.

---

## 역할

- 모든 API 요청의 단일 진입점
- 라우팅 및 로드밸런싱
- **인증/인가 처리**
- **예매 서비스에 대한 대기열 및 입장 제어**
  ![waiting queue pic](https://github.com/user-attachments/assets/bb04e20c-3bc5-4496-8335-e1679582e248)
- 이중화 구성으로 고가용성 확보

---
## 주요 기능
1. **대기열 등록 및 입장 제어**
   - 현재 입장 인원이 최대 수용 인원 미만일 경우 즉시 입장 허용
   - 최대 수용 인원 초과 시 자동으로 대기열에 등록
   - 이미 입장이 허용된 사용자는 대기열 재등록 방지
   - 대기 중 다시 등록 요청 시, 대기열의 뒤로 밀려나감(새로고침 방지)

2. **실시간 상태 업데이트 (SSE)**
   - 대기 중인 사용자들은 10초 주기로 실시간 대기 순번 정보를 받음 
   - 특정 사용자의 입장이 허용되면 즉시 알림을 전송
   - 30초 간격의 Heartbeat로 SSE 연결 유지

3. `대기 → 입장 허용` 상태 변경
   - 입장이 허용된 사용자는 **일정 시간 동안만 예매 관련 API에 접근 가능**
   - 입장 허용된 사용자가 입장 허용 목록에서 제거되는 경우
     1. 입장 허용 시간이 만료되어 **스케줄러에 의해 정리**
     2. 사용자가 **브라우저 창을 닫거나 로그아웃함**
   - 제거된 인원 수만큼 **대기 중인 사용자들의 입장을 순차적으로 허용**

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

## 기술 선택 이유

### Redis / Lua Script
Gateway 서버를 수평 확장할 때 대기열 상태를 일관되게 관리하기 위해,  
대기열 정보를 **중앙 집중적으로 관리할 수 있는 저장소**가 필요했습니다.

Redis는 인메모리 기반 데이터 저장소로서 **빠른 접근 속도**를 제공하며, 싱글 쓰레드 이벤트 루프 구조를 사용하여  
다수의 Gateway 인스턴스가 동시에 접근하더라도 안정적으로 대기열 상태를 공유할 수 있어서 선택했습니다.

또한 핵심 대기열 작업 로직은 **Lua Script**로 구현하여,
여러 Redis 명령을 하나의 스크립트로 묶어 **원자성** 을 보장하고,  
동시 요청 상황에서도 순번 꼬임이나 중복 입장과 같은 Race Condition 문제를 방지할 수 있도록 설계했습니다.

---

### Server-Sent Events (SSE)
초기에는 클라이언트가 주기적으로 대기열 상태를 조회하는 **Polling 방식**을 사용했습니다.  
하지만 동시 접속자가 증가할수록 Gateway가 처리해야 할 요청 수가 급격히 늘어나며, 서버 부하가 발생하는 문제가 있었습니다.

이를 개선하기 위해 **Server-Sent Events(SSE)** 기반 구조로 전환하여,
- 상태 변경(입장 허용)이 발생한 경우에만 서버가 클라이언트에게 이벤트를 전송
- 주기적으로 대기 중인 사용자에게 대기 상태를 전달
- 불필요한 요청을 제거하여 **네트워크 트래픽과 서버 부하를 효과적으로 감소**

그 결과, 대기열 시스템을 **요청 중심(Polling) 구조에서 이벤트 중심(SSE) 구조로 진화**시킬 수 있었습니다.

---
## Redis 데이터 구조
```
1. queue:wait (Sorted Set)
   - 대기 중인 사용자 저장
   - Score: queue:counter에서 받은 값
   - Member: userId (UUID)

2. allowedIn:users (Hash)
   - 입장 허용된 사용자 저장
   - Field: userId (UUID)
   - Value: 입장 허용 타임스탬프 (해당 사용자가 API 요청을 보낼 때마다 갱신됨)

3. queue:counter (String)
   - 대기 순번 생성용 카운터
```
---

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────────┐
│                         NGINX (Reverse Proxy)                       │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                            ┌───────┴───────┐
                            ▼               ▼
                 ┌─────────────────┐ ┌─────────────────┐
                 │ Gateway Server  │ │ Gateway Server  │
                 │ (Waiting Queue) │ │ (Waiting Queue) │
                 │      #1         │ │      #2         │
                 └─────────────────┘ └─────────────────┘
                            │               │
                            └───────┬───────┘
                                    │
              ┌─────────────────────┼─────────────────────┐
              ▼                     ▼                     ▼
    ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
    │  Eureka Server  │   │  Config Server  │   │      Redis      │
    │     (HA x2)     │   │      (Git)      │   │                 │
    └─────────────────┘   └─────────────────┘   └─────────────────┘
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
| `ALLOWED_IN_MAX_CAPACITY`              | 100                                         | 최대 수용 인원                |
| `ALLOWED_IN_DURATION_SECONDS`          | 240                                         | 입장 허용 시간                |
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

          // Auth Service - 공개 API
          .pathMatchers("/api/v1/auth/login").permitAll()
          .pathMatchers("/api/v1/auth/register").permitAll()
          .pathMatchers("/api/v1/auth/refresh").permitAll()
          .pathMatchers("/api/v1/auth/check-email").permitAll()

          // 상품 조회, 아트홀 조회, 티켓 사용
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
