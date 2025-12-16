# Gateway Server

Tickatch MSA의 **API Gateway** 서버입니다.

---

## 역할

- 모든 API 요청의 단일 진입점
- 라우팅 및 로드밸런싱
- 대기열 및 입장 허용 사용자 목록 관리
- 인증/인가 처리
- Rate Limiting, Circuit Breaker
- 이중화 구성으로 고가용성 확보

---

## 기술 스택

| 항목 | 버전 |
|------|------|
| Java | 21 |
| Spring Boot | 4.0.0 |
| Spring Cloud | 2025.1.0 |
| Spring Cloud Gateway | 5.0.x (WebFlux) |
| Spring Security | 7.0.x |

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

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `SERVER_PORT` | 8080 | 서버 포트 |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | http://localhost:8761/eureka/ | Eureka 서버 주소 |
| `ZIPKIN_ENDPOINT` | http://localhost:9411/api/v2/spans | Zipkin 엔드포인트 |

---

## 라우팅 설정

라우팅 설정은 **config-repo**에서 관리됩니다.

| 경로 | 서비스 |
|------|--------|
| `/api/accounts/**` | account-service |
| `/api/transactions/**` | transaction-service |
| `/api/transfers/**` | transfer-service |
| `/api/cards/**` | card-service |
| `/api/ledger/**` | ledger-service |
| `/api/auth/**` | auth-server |
| `/api/users/**` | user-service |

---

## API 엔드포인트

| 엔드포인트 | 설명 |
|------------|------|
| `/actuator/health` | 헬스 체크 |
| `/actuator/gateway/routes` | 라우팅 목록 조회 |
| `/actuator/prometheus` | Prometheus 메트릭 |

---

## 디렉토리 구조

```
gateway-server/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/tickatch/gateway-server/
│       │       ├── GatewayServerApplication.java
│       │       └── config/
│       │           └── SecurityConfig.java
│       └── resources/
│           ├── application.yml
│           └── logback-spring.xml
├── build.gradle
├── settings.gradle
├── Dockerfile
└── README.md
```

---

## Security 설정

현재 기본 설정은 **permitAll()** 상태입니다.

```java
@Bean
public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                    .pathMatchers("/actuator/**").permitAll()
                    .anyExchange().permitAll()
            )
            .build();
}
```

### 추후 구현 예정 (JWT 인증)

| 구성 요소 | 설명 |
|----------|------|
| `JwtAuthenticationFilter` | JWT 토큰 검증 필터 |
| `JwtProperties` | JWT 설정값 (secret, issuer 등) |
| 인증 헤더 주입 | `X-User-Id`, `X-User-Role`, `X-User-Email` 헤더로 내부 서비스에 전달 |

**인증 흐름 (구현 예정)**
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
