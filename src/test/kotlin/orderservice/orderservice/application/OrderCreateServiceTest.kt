package orderservice.orderservice.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import orderservice.orderservice.application.persistence.OrderRepository
import orderservice.orderservice.application.persistence.OutboxRepository
import orderservice.orderservice.domain.order.Order
import orderservice.orderservice.domain.order.OrderCreateRequest
import orderservice.orderservice.domain.order.OrderLineCreateRequest
import orderservice.orderservice.domain.outbox.OutboxEvent
import tools.jackson.databind.ObjectMapper

/**
 * 단위 테스트 — OrderCreateService 비즈니스 로직 검증.
 *
 * 테스트 전략:
 * - Spring 컨텍스트, DB, Redis, Kafka 완전 격리 (외부 의존성 없음)
 * - OrderRepository / OutboxRepository는 MockK로 대체 (가짜 DB)
 * - 오직 OrderCreateService의 로직만 검증
 *
 * 검증 포인트:
 * 1. Order와 OutboxEvent가 각 1회 저장됨 (원자성 의도 확인)
 * 2. OutboxEvent의 eventType = order.created.v1, aggregateType = ORDER
 * 3. 반환값 = 저장된 orderId
 * 4. 도메인 검증 실패 시 Repository 호출 없음
 */
class OrderCreateServiceTest : BehaviorSpec({

    // ─── 외부 의존성을 모두 MockK로 대체 ───────────────────────────────────────
    val orderRepository = mockk<OrderRepository>()
    val outboxRepository = mockk<OutboxRepository>()
    val objectMapper = mockk<ObjectMapper>()

    val service = OrderCreateService(orderRepository, outboxRepository, objectMapper)

    afterEach { clearAllMocks() }

    // ─── 공통 픽스처 ───────────────────────────────────────────────────────────
    val validRequest = OrderCreateRequest(
        buyerId = 1L,
        orderLines = listOf(
            OrderLineCreateRequest(
                productId = 10L,
                productNameSnapshot = "아메리카노",
                unitPrice = 4_500L,
                quantity = 2,
            )
        ),
    )

    // ─── 테스트 ────────────────────────────────────────────────────────────────

    given("유효한 주문 생성 요청") {

        `when`("create() 호출") {

            then("OrderRepository.save()와 OutboxRepository.save()가 각 1회 호출되고 orderId를 반환한다") {
                // given — Repository Mock 설정
                val savedOrder = mockk<Order>()
                every { savedOrder.id } returns 1L
                every { savedOrder.buyerId } returns 1L
                every { savedOrder.totalAmount } returns 9_000L
                every { savedOrder.orderLines } returns emptySet()

                every { orderRepository.save(any()) } returns savedOrder
                every { objectMapper.writeValueAsString(any()) } returns """{"orderId":1}"""
                every { outboxRepository.save(any()) } returnsArgument 0

                // when
                val result = service.create(validRequest)

                // then
                result shouldBe 1L
                verify(exactly = 1) { orderRepository.save(any()) }
                verify(exactly = 1) { outboxRepository.save(any()) }
            }

            then("OutboxEvent는 eventType=order.created.v1, aggregateType=ORDER로 저장된다") {
                val savedOrder = mockk<Order>()
                every { savedOrder.id } returns 7L
                every { savedOrder.buyerId } returns 1L
                every { savedOrder.totalAmount } returns 9_000L
                every { savedOrder.orderLines } returns emptySet()

                every { orderRepository.save(any()) } returns savedOrder
                every { objectMapper.writeValueAsString(any()) } returns """{"orderId":7}"""
                every { outboxRepository.save(any()) } returnsArgument 0

                service.create(validRequest)

                // OutboxEvent INSERT-only 계약 검증
                verify {
                    outboxRepository.save(
                        match { event: OutboxEvent ->
                            event.eventType == "order.created.v1" &&
                            event.aggregateType == "ORDER" &&
                            event.aggregateId == "7"
                        }
                    )
                }
            }
        }
    }

    given("buyerId가 0인 잘못된 요청") {
        val invalidRequest = validRequest.copy(buyerId = 0L)

        `when`("create() 호출") {
            then("Order 도메인 검증에서 IllegalArgumentException 발생, Repository는 호출되지 않는다") {
                shouldThrow<IllegalArgumentException> {
                    service.create(invalidRequest)
                }
                // DB 저장 시도 없음 — 도메인 검증이 Repository 호출 전에 차단
                verify(exactly = 0) { orderRepository.save(any()) }
                verify(exactly = 0) { outboxRepository.save(any()) }
            }
        }
    }

    given("orderLines가 빈 리스트인 잘못된 요청") {
        val invalidRequest = validRequest.copy(orderLines = emptyList())

        `when`("create() 호출") {
            then("Order 도메인 검증에서 IllegalArgumentException 발생") {
                shouldThrow<IllegalArgumentException> {
                    service.create(invalidRequest)
                }
                verify(exactly = 0) { orderRepository.save(any()) }
                verify(exactly = 0) { outboxRepository.save(any()) }
            }
        }
    }
})