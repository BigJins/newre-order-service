package orderservice.orderservice.domain.order

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 단위 테스트 — OrderStatus 상태 전이 검증.
 *
 * 외부 의존성 없음 (Spring 컨텍스트, DB, Redis, Kafka 모두 격리).
 * 오직 OrderStatus의 canTransitionTo() 로직만 테스트.
 *
 * AllMart 문제 해결 검증:
 * - 상태 전이 순서 강제 (예: PENDING_PAYMENT → PREPARING 직접 전환 불가)
 * - when exhaustive → 새 상태 추가 시 이 테스트가 누락을 컴파일 타임에 감지
 */
class OrderStatusTest : BehaviorSpec({

    given("PENDING_PAYMENT 상태") {
        `when`("PAID로 전환") {
            then("성공") {
                OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PAID) shouldBe true
            }
        }
        `when`("CANCELLED로 전환") {
            then("성공") {
                OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.CANCELLED) shouldBe true
            }
        }
        `when`("PREPARING으로 직접 전환 (결제 생략)") {
            then("실패 — 순서 강제") {
                OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PREPARING) shouldBe false
            }
        }
        `when`("DISPATCHED로 직접 전환") {
            then("실패") {
                OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.DISPATCHED) shouldBe false
            }
        }
    }

    given("PAID 상태") {
        `when`("PREPARING으로 전환") {
            then("성공") {
                OrderStatus.PAID.canTransitionTo(OrderStatus.PREPARING) shouldBe true
            }
        }
        `when`("CANCELLED로 전환") {
            then("성공") {
                OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED) shouldBe true
            }
        }
        `when`("PENDING_PAYMENT로 되돌리기") {
            then("실패 — 역방향 전환 불가") {
                OrderStatus.PAID.canTransitionTo(OrderStatus.PENDING_PAYMENT) shouldBe false
            }
        }
    }

    given("PREPARING 상태") {
        `when`("DISPATCHED로 전환") {
            then("성공") {
                OrderStatus.PREPARING.canTransitionTo(OrderStatus.DISPATCHED) shouldBe true
            }
        }
        `when`("CANCELLED로 전환") {
            then("성공") {
                OrderStatus.PREPARING.canTransitionTo(OrderStatus.CANCELLED) shouldBe true
            }
        }
    }

    given("DISPATCHED 상태") {
        `when`("DELIVERED로 전환") {
            then("성공") {
                OrderStatus.DISPATCHED.canTransitionTo(OrderStatus.DELIVERED) shouldBe true
            }
        }
        `when`("CANCELLED로 전환") {
            then("성공 — 배송 중에도 취소 가능") {
                OrderStatus.DISPATCHED.canTransitionTo(OrderStatus.CANCELLED) shouldBe true
            }
        }
    }

    given("DELIVERED 상태 (종료)") {
        `when`("어떤 상태로도 전환") {
            then("모두 실패 — 종료 상태는 전이 없음") {
                OrderStatus.entries.forEach { next ->
                    OrderStatus.DELIVERED.canTransitionTo(next) shouldBe false
                }
            }
        }
    }

    given("CANCELLED 상태 (종료)") {
        `when`("어떤 상태로도 전환") {
            then("모두 실패 — 취소 후 재활성화 불가") {
                OrderStatus.entries.forEach { next ->
                    OrderStatus.CANCELLED.canTransitionTo(next) shouldBe false
                }
            }
        }
    }
})