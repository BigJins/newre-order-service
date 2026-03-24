package orderservice.orderservice.domain.outbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * 단위 테스트 — OutboxEvent 도메인 불변식 검증.
 *
 * 외부 의존성 없음. 오직 OutboxEvent 팩토리 메서드의 검증 로직만 테스트.
 *
 * AllMart 문제 해결 검증:
 * - private constructor 강제 → 직접 생성 불가, of() factory만 사용
 * - INSERT only 계약 확인 (update 메서드 없음)
 */
class OutboxEventTest : BehaviorSpec({

    given("OutboxEvent.of() factory") {

        `when`("유효한 인자") {
            val event = OutboxEvent.of(
                eventType     = "ORDER_CREATED",
                aggregateType = "ORDER",
                aggregateId   = "1",
                payload       = """{"orderId":1}""",
            )

            then("id는 null (저장 전)") {
                event.id.shouldBeNull()
            }
            then("eventType이 그대로 저장됨") {
                event.eventType shouldBe "ORDER_CREATED"
            }
            then("aggregateType이 그대로 저장됨") {
                event.aggregateType shouldBe "ORDER"
            }
            then("aggregateId가 그대로 저장됨") {
                event.aggregateId shouldBe "1"
            }
            then("createdAt이 자동 설정됨") {
                event.createdAt shouldNotBe null
            }
        }

        `when`("eventType이 빈 문자열") {
            then("IllegalArgumentException 발생") {
                shouldThrow<IllegalArgumentException> {
                    OutboxEvent.of(
                        eventType     = "",
                        aggregateType = "ORDER",
                        aggregateId   = "1",
                        payload       = """{"orderId":1}""",
                    )
                }
            }
        }

        `when`("aggregateId가 공백") {
            then("IllegalArgumentException 발생") {
                shouldThrow<IllegalArgumentException> {
                    OutboxEvent.of(
                        eventType     = "ORDER_CREATED",
                        aggregateType = "ORDER",
                        aggregateId   = "   ",
                        payload       = """{"orderId":1}""",
                    )
                }
            }
        }

        `when`("payload가 빈 문자열") {
            then("IllegalArgumentException 발생") {
                shouldThrow<IllegalArgumentException> {
                    OutboxEvent.of(
                        eventType     = "ORDER_CREATED",
                        aggregateType = "ORDER",
                        aggregateId   = "1",
                        payload       = "",
                    )
                }
            }
        }
    }

    given("OutboxEvent INSERT-only 계약") {
        `when`("OutboxEvent 클래스 메서드 목록 확인") {
            then("update / delete 관련 메서드가 존재하지 않음") {
                val methods = OutboxEvent::class.members.map { it.name }
                (methods.none { it.lowercase().contains("update") }) shouldBe true
                (methods.none { it.lowercase().contains("delete") }) shouldBe true
            }
        }
    }
})