package orderservice.orderservice.domain.order

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * 단위 테스트 — Order Aggregate 도메인 로직 검증.
 *
 * 외부 의존성 없음. Spring 컨텍스트, DB, Redis, Kafka 완전 격리.
 * 오직 Order 클래스의 비즈니스 규칙만 테스트.
 */
class OrderTest : BehaviorSpec({

    val sampleLine = OrderLine(
        productId = 1L,
        productNameSnapshot = "아메리카노",
        unitPrice = 4_500L,
        quantity = 2,
    )

    given("Order.create()") {

        `when`("유효한 buyerId와 orderLines") {
            val order = Order.create(buyerId = 1L, orderLines = listOf(sampleLine))

            then("totalAmount = unitPrice * quantity 자동 계산") {
                order.totalAmount shouldBe 9_000L
            }
            then("초기 상태는 PENDING_PAYMENT") {
                order.status shouldBe OrderStatus.PENDING_PAYMENT
            }
            then("orderLines가 그대로 저장됨") {
                order.orderLines.size shouldBe 1
            }
            then("id는 null (저장 전)") {
                order.id shouldBe null
            }
        }

        `when`("여러 항목의 totalAmount") {
            val lines = listOf(
                OrderLine(productId = 1L, productNameSnapshot = "A", unitPrice = 1_000L, quantity = 3),
                OrderLine(productId = 2L, productNameSnapshot = "B", unitPrice = 2_000L, quantity = 2),
            )
            val order = Order.create(buyerId = 1L, orderLines = lines)

            then("각 항목의 unitPrice * quantity 합산") {
                order.totalAmount shouldBe 7_000L
            }
        }

        `when`("buyerId가 0") {
            then("IllegalArgumentException 발생") {
                shouldThrow<IllegalArgumentException> {
                    Order.create(buyerId = 0L, orderLines = listOf(sampleLine))
                }
            }
        }

        `when`("buyerId가 음수") {
            then("IllegalArgumentException 발생") {
                shouldThrow<IllegalArgumentException> {
                    Order.create(buyerId = -1L, orderLines = listOf(sampleLine))
                }
            }
        }

        `when`("orderLines가 빈 리스트") {
            then("IllegalArgumentException 발생") {
                shouldThrow<IllegalArgumentException> {
                    Order.create(buyerId = 1L, orderLines = emptyList())
                }
            }
        }
    }

    given("PENDING_PAYMENT 상태 주문") {
        val order = Order.create(buyerId = 1L, orderLines = listOf(sampleLine))

        `when`("markAsPaid() 호출") {
            then("PAID 상태로 전환되고 confirmedAt이 설정됨") {
                order.markAsPaid()
                order.status shouldBe OrderStatus.PAID
                order.confirmedAt.shouldNotBeNull()
            }
        }
    }

    given("PENDING_PAYMENT 상태 주문 — 취소") {
        `when`("cancel() 호출") {
            val order = Order.create(buyerId = 1L, orderLines = listOf(sampleLine))
            order.cancel()

            then("CANCELLED 상태로 전환") {
                order.status shouldBe OrderStatus.CANCELLED
            }
            then("cancelledAt이 설정됨") {
                order.cancelledAt.shouldNotBeNull()
            }
        }
    }

    given("PENDING_PAYMENT 상태 주문 — 잘못된 전이") {
        `when`("markAsPreparing() 직접 호출 (결제 생략)") {
            then("IllegalStateException 발생") {
                val order = Order.create(buyerId = 1L, orderLines = listOf(sampleLine))
                shouldThrow<IllegalStateException> {
                    order.markAsPreparing()
                }
            }
        }
    }

    given("PAID 상태 주문") {
        `when`("markAsPreparing() 호출") {
            val order = Order.create(buyerId = 1L, orderLines = listOf(sampleLine))
            order.markAsPaid()
            order.markAsPreparing()

            then("PREPARING 상태로 전환") {
                order.status shouldBe OrderStatus.PREPARING
            }
        }
    }

    given("PREPARING 상태 주문") {
        `when`("markAsDispatched(deliveryId) 호출") {
            val order = Order.create(buyerId = 1L, orderLines = listOf(sampleLine))
            order.markAsPaid()
            order.markAsPreparing()
            order.markAsDispatched(deliveryId = 99L)

            then("DISPATCHED 상태로 전환") {
                order.status shouldBe OrderStatus.DISPATCHED
            }
            then("deliveryId가 설정됨") {
                order.deliveryId shouldBe 99L
            }
        }
    }

    given("DELIVERED 상태 주문 (종료)") {
        `when`("cancel() 호출") {
            then("IllegalStateException — 배달 완료 후 취소 불가") {
                val order = Order.create(buyerId = 1L, orderLines = listOf(sampleLine))
                order.markAsPaid()
                order.markAsPreparing()
                order.markAsDispatched(99L)
                order.markAsDelivered()

                shouldThrow<IllegalStateException> {
                    order.cancel()
                }
            }
        }
    }

    given("CANCELLED 상태 주문 (종료)") {
        `when`("markAsPaid() 호출") {
            then("IllegalStateException — 취소 후 재활성화 불가") {
                val order = Order.create(buyerId = 1L, orderLines = listOf(sampleLine))
                order.cancel()

                shouldThrow<IllegalStateException> {
                    order.markAsPaid()
                }
            }
        }
    }
})