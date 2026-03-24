package orderservice.orderservice.domain.outbox

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * Outbox 이벤트 — INSERT ONLY. UPDATE 절대 금지.
 *
 * AllMart 문제 해결:
 * - UPDATE 방식(eventType 덮어쓰기) → ORDER_CREATED 이력 소실, Kafka 재처리 불가
 * - INSERT only로 이벤트 이력 영구 보존, Debezium CDC가 binlog에서 감지
 *
 * 불변식:
 * - private constructor + of() factory 강제 → 직접 생성 불가
 * - Order 저장과 반드시 같은 트랜잭션에서 INSERT (@Transactional in OrderCreateService)
 */
@Table("outbox_event")
data class OutboxEvent @PersistenceCreator private constructor(
    @Id val id: Long? = null,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: String,
    val payload: String,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun of(
            eventType: String,
            aggregateType: String,
            aggregateId: String,
            payload: String
        ): OutboxEvent {
            require(eventType.isNotBlank()) { "eventType은 비어있을 수 없습니다." }
            require(aggregateType.isNotBlank()) { "aggregateType은 비어있을 수 없습니다." }
            require(aggregateId.isNotBlank()) { "aggregateId는 비어있을 수 없습니다." }
            require(payload.isNotBlank()) { "payload는 비어있을 수 없습니다." }

            return OutboxEvent(
                eventType = eventType,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                payload = payload
            )
        }
    }
}