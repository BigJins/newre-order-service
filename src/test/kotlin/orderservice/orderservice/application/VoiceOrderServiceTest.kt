package orderservice.orderservice.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import orderservice.orderservice.adapter.redis.RedisVoiceOrderStore
import orderservice.orderservice.application.provided.OrderCreator
import orderservice.orderservice.domain.order.OrderCreateRequest
import orderservice.orderservice.domain.order.OrderLineCreateRequest

class VoiceOrderServiceTest : BehaviorSpec({

    val voiceOrderStore = mockk<RedisVoiceOrderStore>()
    val orderCreator = mockk<OrderCreator>()

    val service = VoiceOrderService(voiceOrderStore, orderCreator)

    afterEach { clearAllMocks() }

    val sampleRequest = OrderCreateRequest(
        buyerId = 1L,
        orderLines = listOf(
            OrderLineCreateRequest(productId = 10L, productNameSnapshot = "카페라떼", unitPrice = 5_000L, quantity = 1)
        ),
    )

    given("음성 주문 저장 요청") {
        `when`("save() 호출") {
            then("RedisVoiceOrderStore.save()가 1회 호출된다") {
                justRun { voiceOrderStore.save(1L, sampleRequest) }
                service.save(1L, sampleRequest)
                verify(exactly = 1) { voiceOrderStore.save(1L, sampleRequest) }
            }
        }
    }

    given("음성 주문 확정 — Redis에 데이터 존재") {
        `when`("confirm() 호출") {
            then("OrderCreator.create()가 호출되고 orderId를 반환, Redis가 삭제된다") {
                every { voiceOrderStore.load(1L) } returns sampleRequest
                every { orderCreator.create(sampleRequest) } returns 42L
                justRun { voiceOrderStore.delete(1L) }

                val orderId = service.confirm(1L)

                orderId shouldBe 42L
                verify(exactly = 1) { orderCreator.create(sampleRequest) }
                verify(exactly = 1) { voiceOrderStore.delete(1L) }
            }
        }
    }

    given("음성 주문 확정 — Redis에 데이터 없음 (TTL 만료)") {
        `when`("confirm() 호출") {
            then("IllegalStateException 발생, OrderCreator는 호출되지 않는다") {
                every { voiceOrderStore.load(1L) } returns null

                shouldThrow<IllegalStateException> { service.confirm(1L) }

                verify(exactly = 0) { orderCreator.create(any()) }
                verify(exactly = 0) { voiceOrderStore.delete(any()) }
            }
        }
    }

    given("음성 주문 취소 요청") {
        `when`("cancel() 호출") {
            then("RedisVoiceOrderStore.delete()가 1회 호출된다") {
                justRun { voiceOrderStore.delete(1L) }
                service.cancel(1L)
                verify(exactly = 1) { voiceOrderStore.delete(1L) }
            }
        }
    }
})
