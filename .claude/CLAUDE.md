# CLAUDE.md — Order Service 프로젝트 컨텍스트

> 이 파일은 Claude AI가 이 프로젝트의 배경과 설계 결정을 이해하기 위한 컨텍스트 문서입니다.
> 프로젝트 루트에 두면 매번 설명 없이 맥락을 유지할 수 있습니다.

---

## 개발자 정보
- 이름: 김대진 (Kim Daejin)
- 목표: 토스페이먼츠 Server Developer (3년 이하) 지원
- GitHub: https://github.com/BigJins

---

## 프로젝트 목적

AllMart(Java/Spring Boot 모놀리식)에서 발견한 구조적 문제들을 해결하는 재설계 프로젝트.
"왜 이 기술인가"의 근거가 모두 AllMart 코드에 있음.

---

## 인프라 구성 (실제 compose.yaml 기반)

### .env 값
```
DB_NAME=order_test
DB_USER=tester
DB_PASSWORD=eowls2
DB_ROOT_PASSWORD=order_root
DB_PORT=3306
DB_HOST=localhost
```

### 서비스 구성
```
MySQL       localhost:3306   (order_test DB)
            Debezium 연동을 위한 binlog 설정 포함
            --server-id=223344
            --log-bin=mysql-bin
            --binlog-format=ROW
            --binlog-row-image=FULL

Kafka       3-Broker KRaft 클러스터 (Zookeeper 없음)
            kafka-broker-1  localhost:19092
            kafka-broker-2  localhost:19093
            kafka-broker-3  localhost:19094
            CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk

Kafka UI    localhost:8090   (provectuslabs/kafka-ui)
            클러스터명: ALLMart_test

Debezium    localhost:8083   (debezium/connect:3.0.0.Final)
            binlog → Kafka 전파 담당
            CONFIG_STORAGE_TOPIC=my_connect_configs
            OFFSET_STORAGE_TOPIC=my_connect_offsets

Redis       localhost:6379   (음성 주문 TTL 저장 + 처리율 제한 토큰 버킷)
```

### application.yaml 구성 전략
```
application.yaml        → 공통 설정 (spring.application.name만)
application-local.yaml  → 로컬 개발 설정
  - docker compose lifecycle-management: start_only
  - datasource: jdbc:mysql://localhost:3306/order_test
  - kafka: localhost:19092,19093,19094
```

### 로컬 실행 방법
```bash
# 1. 인프라 시작
docker compose up -d

# 2. Debezium 커넥터 등록 (최초 1회 — Order Service outbox)
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @debezium/order-outbox-connector.json

# 3. Debezium 커넥터 등록 (최초 1회 — Delivery Service outbox)
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @C:/newre/delivery-service/debezium/delivery-outbox-connector.json

# 4. Kafka UI 확인
http://localhost:8090

# 4. 애플리케이션 실행 (IntelliJ)
# VM Options: -Dspring.profiles.active=local
```

---

## AllMart에서 발견한 문제 (모든 기술 선택의 근거)

### 🔴 문제 1: Order ↔ Delivery 강결합
```java
// OrderEntity.java
@ManyToOne
@JoinColumn(name = "deliveryID", nullable = true)
private DeliveryEntity delivery; // Order가 Delivery를 직접 참조

// OrderJpaRepository.java
List<OrderEntity> findByDeliveryStatus(DeliveryStatus deliveryStatus);

// OutboxEventListener.java — 핵심 문제
@KafkaListener(topics = "vgdb.vgdb.tbl_outbox")
public void handleOutboxEvent(String message) {
    deliveryService.createDelivery(orderId, customerId, "ONLINE");
    // Kafka 받아서 같은 프로세스 안의 DeliveryService 직접 호출
    // 서비스 분리 효과 없음, 장애 격리 불가
}
```

### 🔴 문제 2: Outbox UPDATE → 이벤트 이력 소실
```java
// OutboxEntity.java
public void updateEventType(String newEventType) {
    this.eventType = newEventType; // 기존 row 덮어씀
}
// ORDER_CREATED → ORDER_COMPLETED 전환 시 이력 사라짐
// Kafka 재처리 불가 → 배달 생성 누락 위험
```

### 🔴 문제 3: Kafka auto-commit → 메시지 유실
```yaml
enable-auto-commit: true # 처리 전에 offset 자동 커밋
# Consumer 처리 중 서버 다운 시 메시지 영구 유실
```

### 🔴 문제 4: PESSIMISTIC_WRITE → HikariCP 고갈
```java
// DriverRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<DriverEntity> findAvailableDriver();
// 부하 테스트 800 RPS → HikariCP pending 180건 급증 (Grafana 실측)
```

### 🟠 문제 5: Controller → Repository 직접 참조
```java
// OrderController.java
private final TemporaryOrderRepository temporaryOrderRepository; // 직접 참조
// DriverController — Entity를 Request로 직접 받음
public ResponseEntity<DriverEntity> addDriver(@RequestBody DriverEntity driver)
```

### 🟠 문제 6: OrderItem → Product 직접 참조
```java
@ManyToOne(fetch = FetchType.LAZY)
private Product product; // productNameSnapshot 없음
// 상품 삭제/변경 시 주문 이력 깨짐
```

### 🟡 문제 7: TemporaryOrderScheduler
```java
@Scheduled(cron = "0 00 17,16 * * ?") // 주석은 10시,16시 → 실제는 17시,16시 (오류)
// findByStatus(PENDING) → 전체 메모리 로드
// 처리 중 실패 → 재시작 시 중복 처리
```

### 🔴 문제 8: currentDeliveryCount 감소 로직 미호출 (DeliveryService)
```java
// DriverEntity.java — 메서드는 정의됨
public void decrementCurrentDeliveryCount() { ... }

// DeliveryService.java — COMPLETED/CANCELLED 시 호출 없음
// 운영 시 모든 드라이버가 maxDeliveryCount에 도달 → 신규 배정 불가
```

### 🔴 문제 9: publishKafkaEvent, updateOutboxEvent — 정의만 있고 호출 없음
```java
// DeliveryService.java 내 dead code
private void publishKafkaEvent(...) { kafkaTemplate.send(...); }
private void updateOutboxEvent(...) { outboxRepository.save(...); }
// Delivery → Order 이벤트가 실제로 발행되지 않음
// Order는 배달 상태 변화를 영원히 알 수 없음
```

### 🟠 문제 10: START 상태가 즉시 IN_PROGRESS로 덮어써짐
```java
// DeliveryService.java
if (newStatus == DeliveryStatus.START) {
    delivery.updateStatus(DeliveryStatus.IN_PROGRESS); // 즉시 덮어씀
}
// START 상태가 DB에 의미 없이 저장됐다가 바로 IN_PROGRESS로 전환
// 상태 설계 불일치
```

### 🟠 문제 11: Redis에 JPA Entity 직렬화
```java
// DeliveryService.java
String redisValue = objectMapper.writeValueAsString(delivery);
// DeliveryEntity 내부에 @ManyToOne(fetch = LAZY) DriverEntity 존재
// → LazyInitializationException 또는 순환참조 위험
```

---

## 기술 선택 이유

| 기술 | AllMart 문제 | 선택 이유 | 대안 대비 장점 |
|------|-------------|----------|--------------|
| Kotlin 2.2 | BigDecimal null 체크 개발자 직접 작성 | Null Safety 컴파일 타임 차단 | Java 25: null-restricted 아직 draft |
| Spring Boot 4 | Spring API 플랫폼 타입 반환 | JSpecify 전 포트폴리오 null-safe | Boot 3: 플랫폼 타입으로 반환 |
| Spring Data JDBC | JPA N+1, Lazy Loading, Kotlin 궁합 나쁨 | Aggregate 경계 명확, data class 완벽 지원 | JPA: 영속성 컨텍스트 복잡도 |
| Outbox INSERT | UPDATE 방식 → 이벤트 이력 소실 | DB 트랜잭션 원자성 보장 | 직접 produce: 발행 실패 위험 |
| Debezium CDC | binlog 기반 이벤트 전파 | 커밋된 데이터만 전파, 앱 코드 수정 없음 | Polling: DB 부하 발생 |
| ack-mode: record | auto-commit → 메시지 유실 | 처리 완료 후 수동 커밋 | auto-commit: 처리 전 커밋 위험 |
| Redisson RLock | PESSIMISTIC_WRITE → HikariCP 180건 | DB 커넥션 점유 없이 동시성 제어 | DB 락: 커넥션 점유, 낙관적 락: 재시도 폭발 |
| Spring Data JDBC (Delivery) | JPA Lazy Loading → Redis 직렬화 폭발, PESSIMISTIC_WRITE 사용 가능 | JDBC는 Lazy Loading 개념 없음, JPA 락 API 자체 불가 → 구조적 제거 | JPA: Lazy/Lock 문제 여전히 존재 |
| 헥사고날 아키텍처 | Controller → Repository 직접 참조 | 비즈니스 로직이 외부 기술에 독립 | 계층형: 기술 교체 시 Service 전체 수정 |
| Redis TTL + 토큰 버킷 | TemporaryOrder Scheduler 오류, 중복 처리 + 음성 주문 API 남용 | TTL 만료로 자동 폐기, Scheduler 제거 / 같은 Redis로 처리율 제한까지 확장 | Scheduler: cron 오류, 중복 처리 위험 |
| KRaft Kafka 3-Broker | (신규) | Zookeeper 없이 고가용성 클러스터 구성 | 단일 브로커: SPOF |

---

## 도메인 설계

### Order Aggregate
```kotlin
// Order = Aggregate Root
// @PersistenceCreator private constructor → DB 읽기 전용, 팩토리 메서드로만 생성 강제
@Table("orders")
class Order @PersistenceCreator private constructor(
    @Id val id: Long? = null,
    val buyerId: Long,                          // non-null
    val totalAmount: Long,                      // Long (원 단위), null 불가
    var status: OrderStatus = OrderStatus.PENDING_PAYMENT,
    @MappedCollection(idColumn = "order_id")
    val orderLines: Set<OrderLine> = emptySet(),
    var deliveryId: Long? = null,               // Delivery는 ID로만 참조 (markAsDispatched 시 변경)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var confirmedAt: LocalDateTime? = null,
    var cancelledAt: LocalDateTime? = null
) {
    companion object {
        fun create(buyerId: Long, orderLines: List<OrderLine>): Order
    }
    fun markAsPaid(): Order
    fun markAsPreparing(): Order
    fun markAsDispatched(deliveryId: Long): Order
    fun markAsDelivered(): Order
    fun cancel(): Order
}

// OrderLine = Aggregate 하위 객체 (Order 없이 존재 불가)
@Table("order_lines")
data class OrderLine(
    val productId: Long,
    val productNameSnapshot: String,   // 주문 시점 상품명 (불변)
    val unitPrice: Long,               // 주문 시점 단가 (불변)
    val quantity: Int
)
```

### OrderStatus 상태 전환
```
PENDING_PAYMENT → PAID → PREPARING → DISPATCHED → DELIVERED
                                                      (종료)
모든 상태 → CANCELLED (종료)
when exhaustive → 새 상태 추가 시 컴파일 에러로 누락 방지
```

### OutboxEvent
```kotlin
// INSERT only (UPDATE 절대 금지)
// Order 저장과 반드시 같은 트랜잭션
@Table("outbox_event")
data class OutboxEvent @PersistenceCreator private constructor(
    @Id val id: Long? = null,
    val eventType: String,       // Kafka 토픽명 그대로 (order.created.v1, order.dispatched.v1 등)
    val aggregateType: String,   // ORDER
    val aggregateId: String,     // orderId.toString()
    val payload: String,         // JSON
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun of(eventType: String, aggregateType: String, aggregateId: String, payload: String): OutboxEvent
    }
}
```

### 음성 주문 (TemporaryOrder 대체) + 처리율 제한
```
기존: TemporaryOrder 테이블 + Scheduler (문제 7)
대체: Redis TTL 기반 + 토큰 버킷 처리율 제한

흐름:
음성 인식 → RateLimiter.isAllowed(userId) 확인 (토큰 버킷, Redis)
    ↓ 허용
Redis 저장 (key: voice:order:{userId}, TTL: 10분)
    ↓
사용자 확인/수정 (10분 안에)
    ↓
확정 API 호출 → Order 생성 + OutboxEvent INSERT (같은 트랜잭션)
    ↓ 또는
TTL 만료 → 자동 폐기 (DB 쓰레기 없음, Scheduler 불필요)
    ↓ 거부 (토큰 소진)
429 Too Many Requests 반환

토큰 버킷 설정:
- 버킷 용량: 5회 (burst 허용)
- 리필 속도: 초당 1토큰
- Lua 스크립트로 Redis 내 원자적 실행 → 분산 환경 race condition 없음
- Key: rate_limit:order:{userId}
```

---

## 패키지 구조

```
orderservice/orderservice/
├── domain/
│   ├── order/
│   │   ├── Order.kt              # Aggregate Root
│   │   ├── OrderLine.kt          # Aggregate 하위
│   │   ├── OrderStatus.kt        # 상태 전환 검증 포함
│   │   └── OrderCreateRequest.kt # 도메인 입력 객체
│   └── outbox/
│       └── OutboxEvent.kt        # INSERT only
│
├── application/
│   ├── provided/                 # 인바운드 포트 (인터페이스)
│   │   ├── OrderCreator.kt
│   │   ├── OrderFinder.kt
│   │   └── OrderStatusUpdater.kt # delivery 이벤트 수신 시 상태 갱신
│   ├── OrderCreateService.kt     # 주문 생성 + Outbox INSERT
│   ├── OrderQueryService.kt      # readOnly 조회
│   ├── OrderStatusUpdateService.kt # Delivery 이벤트 → Order 상태 변경 + Outbox INSERT
│   └── VoiceOrderService.kt      # 음성 주문 Redis TTL 처리
│
└── adapter/
    ├── web/
    │   ├── OrderApi.kt
    │   ├── VoiceOrderApi.kt
    │   ├── GlobalExceptionHandler.kt
    │   └── dto/
    ├── persistence/              # Spring Data CrudRepository 직접 정의
    │   ├── OrderRepository.kt    # CrudRepository<Order, Long>
    │   └── OutboxRepository.kt   # CrudRepository<OutboxEvent, Long>
    ├── redis/
    │   ├── RedisVoiceOrderStore.kt  # 구체 클래스 직접 주입
    │   └── RateLimiter.kt           # 토큰 버킷 처리율 제한 (Lua 스크립트)
    └── kafka/
        └── DeliveryEventConsumer.kt # delivery.assigned/completed/cancelled.v1 수신
```

---

## 핵심 흐름

### 주문 생성 + Order ↔ Delivery 전체 흐름
```
[Order Service]                          [Delivery Service]

POST /api/orders
  → OrderCreateService [@Transactional]
      orders INSERT
      outbox_event INSERT (ORDER_CREATED)
  → Debezium → Kafka: order.created.v1
                                           수신: ORDER_CREATED
                                           Redisson RLock(driverId)
                                           driver.assignDelivery()
                                           deliveries INSERT
                                           outbox_event INSERT (DELIVERY_ASSIGNED)
                                         → Debezium → Kafka: delivery.assigned.v1
  수신: DELIVERY_ASSIGNED
  Order.markAsDispatched(deliveryId)
  outbox_event INSERT (ORDER_DISPATCHED)

                                           배달 완료 처리
                                           driver.decrementCurrentDeliveryCount()
                                           outbox_event INSERT (DELIVERY_COMPLETED)
                                         → Debezium → Kafka: delivery.completed.v1
  수신: DELIVERY_COMPLETED
  Order.markAsDelivered()
  outbox_event INSERT (ORDER_DELIVERED)
```

### 음성 주문
```
POST /api/orders/voice  → Redis SET voice:order:{userId} EX 600
POST /api/orders/voice/confirm → Order 생성 + Redis DEL
TTL 만료 → 자동 폐기
```

---

## 코드 작성 규칙

### Kotlin
```kotlin
// Order는 class (도메인 행동 포함, private constructor)
// OrderLine은 data class (순수 값 객체)
// val = 기본, var = 상태가 변하는 필드만
// require() 로 도메인 불변식 검증
```

### Spring Data JDBC
```kotlin
// 사용: @Table, @Id, @MappedCollection
// 금지: @Entity, @ManyToOne, @OneToMany (JPA 어노테이션)
// 금지: FetchType (JDBC에 없음)
// 필수: save() 명시적 호출
```

### 아키텍처
```
금지: Controller → Repository 직접 참조
금지: Entity를 Request/Response로 사용
금지: Aggregate 간 직접 참조 (ID로만)
필수: OutboxEvent는 INSERT only
필수: Order와 OutboxEvent는 같은 트랜잭션

Repository 구조 (의도적 단순화):
- 포트 인터페이스(application/required) + 래퍼(JdbcXxx) + Spring Data = 과설계
- Spring Data JDBC 인터페이스를 adapter/persistence에 두고 서비스가 직접 주입
- Redis 구체 클래스(RedisVoiceOrderStore)도 직접 주입 — 추상화 불필요
```

### H2 테스트 설정 (중요)
```
CASE_INSENSITIVE_IDENTIFIERS=TRUE 필수:
Spring Data JDBC가 "orders"(소문자 quoted) 쿼리 생성
H2는 기본적으로 ORDERS(대문자)로 저장 → 불일치
이 플래그로 quoted identifier도 case-insensitive하게 처리

flyway.enabled: false 필수:
테스트 프로파일에서 Flyway가 H2에서 실행되면 에러
schema.sql로 테스트 스키마 직접 관리
```

---

## DB 스키마

```sql
CREATE TABLE orders (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    buyer_id     BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT',
    total_amount BIGINT      NOT NULL,
    delivery_id  BIGINT,
    created_at   DATETIME    NOT NULL,
    confirmed_at DATETIME,
    cancelled_at DATETIME,
    INDEX idx_buyer_id (buyer_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);

CREATE TABLE order_lines (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id              BIGINT       NOT NULL,
    product_id            BIGINT       NOT NULL,
    product_name_snapshot VARCHAR(200) NOT NULL,
    unit_price            BIGINT       NOT NULL,
    quantity              INT          NOT NULL,
    INDEX idx_order_id (order_id)
);

CREATE TABLE outbox_event (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type     VARCHAR(50)  NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    payload        LONGTEXT     NOT NULL,
    created_at     DATETIME     NOT NULL,
    INDEX idx_created_at (created_at)
);
```

---

## Debezium 커넥터 설정

### EventRouter 토픽 라우팅 전략
- `outbox_event.event_type` 값 = Kafka 토픽명 (예: `order.created.v1`, `delivery.assigned.v1`)
- `route.topic.replacement: ${routedByValue}` → event_type 값을 그대로 토픽명으로 사용
- 커넥터 1개로 여러 토픽 자동 라우팅

### Order Service Outbox 커넥터 (debezium/order-outbox-connector.json)
- `database.server.id: 223344`
- `table.include.list: order_test.outbox_event`
- 발행 토픽: order.created.v1, order.dispatched.v1, order.delivered.v1, order.cancelled.v1

### Delivery Service Outbox 커넥터 (delivery-service/debezium/delivery-outbox-connector.json)
- `database.server.id: 223345` (Order와 다른 ID — binlog 복제 충돌 방지)
- `table.include.list: delivery_db.outbox_event`
- 발행 토픽: delivery.assigned.v1, delivery.completed.v1, delivery.cancelled.v1

---

## Kafka 토픽

| 토픽 | 발행자 | 소비자 | Order 메서드 |
|------|--------|--------|------|
| order.created.v1 | Debezium (order_test.outbox_event) | Delivery Service | — |
| order.dispatched.v1 | Debezium (order_test.outbox_event) | (미래 서비스) | markAsDispatched() 후 발행 |
| order.delivered.v1 | Debezium (order_test.outbox_event) | (미래 서비스) | markAsDelivered() 후 발행 |
| order.cancelled.v1 | Debezium (order_test.outbox_event) | (미래 서비스) | cancel() 후 발행 |
| delivery.assigned.v1 | Debezium (delivery_db.outbox_event) | Order Service | → markAsDispatched(deliveryId) |
| delivery.completed.v1 | Debezium (delivery_db.outbox_event) | Order Service | → markAsDelivered() |
| delivery.cancelled.v1 | Debezium (delivery_db.outbox_event) | Order Service | → cancel() |

---

## 포트폴리오 기여 스토리

### 기여 1: 음성 주문 구조 개선 + Redis 처리율 제한

**배경 (AllMart 문제 7)**
```java
@Scheduled(cron = "0 00 17,16 * * ?") // 주석과 실제 시각 불일치 (오류)
// findByStatus(PENDING) → 전체 메모리 로드
// 처리 중 실패 → 재시작 시 중복 처리
```

**해결 1: Redis TTL로 Scheduler 완전 제거**
```
TemporaryOrder 테이블 삭제 → Redis SET voice:order:{userId} EX 600
TTL 만료 시 자동 폐기 → Scheduler, DB 쓰레기, 중복 처리 모두 소멸
```

**해결 2: 같은 Redis 인프라로 토큰 버킷 처리율 제한 확장**
```
RateLimiter (Lua 스크립트 원자적 실행)
- 버킷 5회 / 초당 1토큰 리필
- Key: rate_limit:order:{userId}
- 초과 시 429 Too Many Requests
- 분산 환경에서도 race condition 없음 (Lua 원자성 보장)
```

**스토리 흐름**: Scheduler 문제 발견 → Redis TTL 도입으로 해결 → 동일 Redis 인프라를 활용해 처리율 제한까지 확장 → 인프라 추가 없이 두 문제 동시 해결

---

## 검증 계획 (이력서 수치)

### Story 1: 음성 주문 구조 개선 + Redis 처리율 제한
```
재현 (Scheduler):
  - TemporaryOrderScheduler cron 오류 → 원하는 시각에 미실행
  - findByStatus(PENDING) 전체 로드 → OOM 위험
재현 (처리율):
  - 동일 userId 반복 호출 → DB/비즈니스 로직 과부하
해결:
  - Redis TTL(10분) → Scheduler 완전 제거
  - 토큰 버킷(Lua 스크립트) → 429 차단
검증:
  - k6로 동일 userId 10회 연속 호출 → 6회 이후 429 확인
  - Scheduler 코드 0줄 (삭제 증거)
```

### Story 2: 드라이버 중복 배정 (Delivery Service)
```
재현: CountDownLatch 동시 50 스레드
      PESSIMISTIC_WRITE → currentDeliveryCount 초과 저장
해결: Redisson RLock(driverId 단위)
검증: 중복 배정 Before/After, HikariCP pending Before/After
```

### Story 3: 메시지 유실 방지
```
재현: Consumer kill -9 → auto-commit → 재처리 불가
해결: ack-mode: record
검증: 유실 건수 50회 시뮬레이션 Before/After
```

### Story 4: Outbox INSERT 이벤트 재처리
```
재현: UPDATE 방식 → ORDER_CREATED 이력 소실
해결: INSERT only
검증: outbox_event 행 수 Before/After, 재처리 성공률
```

### Story 5: 정산 (추후 Spring Batch)
```
재현: 10만 건 처리 중 강제 실패 → 중복 처리
해결: JpaPagingItemReader chunk=1000
검증: 중복 0건, chunk별 처리시간 비교
```

---

## 테스트 전략

### 단위 테스트 — 도메인 (Kotest BehaviorSpec)
- **원칙**: 모든 외부 의존성을 격리(차단). Spring 컨텍스트, DB, Redis, Kafka 없음.
- **대상**: 오직 해당 클래스의 비즈니스 로직만 테스트.
- **프레임워크**: Kotest `BehaviorSpec` — given/when/then 구조.
- **대상 파일**:
  - `OrderStatusTest` — 상태 전이 exhaustive 검증
  - `OrderTest` — Aggregate 생성·전이·불변식 검증
  - `OutboxEventTest` — INSERT-only 계약, factory 검증

### 단위 테스트 — 서비스 (Kotest BehaviorSpec + MockK)
- **원칙**: 모든 외부 의존성을 격리. Repository(DB)는 MockK 가짜 객체로 대체.
- **방법**: 서비스 인스턴스를 직접 생성, 의존성은 `mockk<T>()`로 주입.
- **검증**: 올바른 메서드가 올바른 인자로 호출되었는지 `verify()`로 확인.
- **대상 파일**:
  - `OrderCreateServiceTest` — Order+Outbox 원자성 의도, eventType 검증, 도메인 검증 실패 시 Repository 미호출
  - `VoiceOrderServiceTest` — Redis 저장/조회/삭제 흐름, TTL 만료 처리

### 통합 테스트 (SpringBootTest + MockMvc)
- **원칙**: API 요청부터 DB 저장까지 시스템의 전체 흐름을 테스트.
- **어노테이션**:
  ```kotlin
  @SpringBootTest       // 전체 스프링 컨텍스트 로드 (실제 Service, Repository 동작)
  @AutoConfigureMockMvc // MockMvc 주입 → 실제 HTTP 요청 시뮬레이션
  @Transactional        // 각 테스트 후 DB 롤백 → 테스트 간 격리
  @ActiveProfiles("test") // H2 인메모리 DB 사용 (application-test.yaml)
  @EmbeddedKafka        // 인메모리 Kafka 브로커 제공
  ```
- **DB**: H2 인메모리 (application-test.yaml + schema.sql)
- **Redis**: `@MockkBean StringRedisTemplate` — 실제 Redis 불필요
- **검증 범위**: 전체 HTTP 요청 → Controller → Service → Repository (H2) → 응답 검증
- **대상 파일**:
  - `OrderApiIntegrationTest` — 주문 생성·조회 전체 흐름, 유효성 검사
  - `VoiceOrderApiIntegrationTest` — 음성 주문 저장·확정(→실제 Order H2 저장)·취소

---

## 진행 상황

- [x] AllMart 코드 분석 완료
- [x] 6가지 문제 정의 완료
- [x] 기술 선택 이유 확정
- [x] 도메인 설계 확정
- [x] 음성 주문 대안 결정 (Redis TTL)
- [x] docker-compose 구성 완료 (KRaft 3-Broker + Debezium + Redis)
- [x] application.yaml / application-local.yaml 구성 (kafka 소문자, ack-mode: record)
- [x] build.gradle.kts 완성 (Kotest, MockK, springmockk, H2)
- [x] application-test.yaml — H2, Redis/Kafka health check 비활성화
- [x] schema.sql — H2 호환 테스트 스키마
- [x] OrderStatus.kt — exhaustive when, 상태 전이 검증
- [x] OrderLine.kt — data class, 스냅샷 패턴
- [x] OrderCreateRequest.kt — 도메인 입력 객체 (HTTP DTO와 분리)
- [x] Order.kt — private constructor + PersistenceCreator + factory
- [x] OutboxEvent.kt — private constructor + PersistenceCreator + of() factory, INSERT only
- [x] application/provided/ — OrderCreator, OrderFinder, OrderStatusUpdater (인바운드 포트)
- [x] OrderCreateService.kt — @Transactional, Order+Outbox 원자적 저장 (eventType=Kafka 토픽명)
- [x] OrderQueryService.kt — readOnly 트랜잭션
- [x] OrderStatusUpdateService.kt — delivery 이벤트 수신 → Order 상태 변경 + Outbox INSERT
- [x] VoiceOrderService.kt — Redis TTL 기반 임시 주문
- [x] adapter/web/dto/ — CreateOrderHttpRequest, OrderDetailResponse, VoiceOrderHttpRequest
- [x] OrderApi.kt — POST/GET, 헥사고날 포트만 의존
- [x] VoiceOrderApi.kt — 음성 주문 저장/확정/취소
- [x] adapter/persistence/ — OrderRepository, OutboxRepository (Spring Data CrudRepository)
- [x] adapter/redis/ — RedisVoiceOrderStore (구체 클래스 직접 주입)
- [x] adapter/redis/RateLimiter.kt — 토큰 버킷 처리율 제한 (Lua 스크립트, key: rate_limit:order:{userId})
- [x] VoiceOrderApi.kt — RateLimiter 연동 (저장 요청 시 isAllowed() 호출, 초과 시 429)
- [x] adapter/kafka/ — DeliveryEventConsumer (delivery.assigned/completed/cancelled.v1 수신)
- [x] 단위 테스트 (도메인) — OrderStatusTest, OrderTest, OutboxEventTest (Kotest BehaviorSpec)
- [x] 단위 테스트 (서비스) — OrderCreateServiceTest (eventType=order.created.v1), VoiceOrderServiceTest
- [x] 통합 테스트 — OrderApiIntegrationTest, VoiceOrderApiIntegrationTest (@SpringBootTest)
- [x] GlobalExceptionHandler — 404/400/500 처리
- [x] debezium/order-outbox-connector.json — route.topic.replacement=${routedByValue}
- [ ] Debezium 커넥터 2개 등록 및 토픽 발행 확인 (Kafka UI)
- [ ] Delivery Service 전체 통합 검증 (order.created.v1 → driver 배정 → delivery.assigned.v1 수신)
- [ ] Redisson RLock 드라이버 배정 동시성 검증 (k6 50 스레드)

---

## 토스페이먼츠 JD 매핑

| JD 항목 | 완성 후 증거 |
|---------|------------|
| 확장성·유연성 높은 시스템 | 헥사고날 + Outbox + CDC + JDBC |
| 선착순 이벤트, 피크 트래픽 | Redisson RLock + Redis 토큰 버킷 + k6 수치 |
| 오차 없는 정산 시스템 | Spring Batch (추후) |
| 무중단 MSA | Order/Delivery 물리 분리 + Kafka |