package orderservice.orderservice.application

import orderservice.orderservice.application.persistence.OrderRepository
import orderservice.orderservice.application.persistence.OutboxRepository
import orderservice.orderservice.application.provided.OrderCreator
import orderservice.orderservice.domain.order.Order
import orderservice.orderservice.domain.order.OrderCreateRequest
import orderservice.orderservice.domain.order.OrderLine
import orderservice.orderservice.domain.outbox.OutboxEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * 주문 생성 유스케이스.
 *
 * 핵심 불변식: Order 저장과 OutboxEvent INSERT가 반드시 같은 트랜잭션.
 * → @Transactional이 실패하면 둘 다 롤백 → Debezium이 binlog 변경분 없음 → Kafka 미발행
 * → 주문 데이터와 이벤트 발행 사이의 불일치(Split-Brain) 원천 차단
 *
 * AllMart 문제 해결:
 * - Outbox UPDATE → 이력 소실: OutboxEvent.of() factory로 INSERT only 강제
 * - Controller → Repository 직접 참조: OrderCreator 포트로만 접근
 */
@Service
@Transactional
class OrderCreateService(
    private val orderRepository: OrderRepository,
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper
) : OrderCreator {

    override fun create(request: OrderCreateRequest): Long {
        val orderLines = request.orderLines.map { line ->
            OrderLine(
                productId = line.productId,
                productNameSnapshot = line.productNameSnapshot,
                unitPrice = line.unitPrice,
                quantity = line.quantity
            )
        }

        val order = Order.create(buyerId = request.buyerId, orderLines = orderLines)
        val savedOrder = orderRepository.save(order)

        val payload = objectMapper.writeValueAsString(
            mapOf(
                "orderId"     to savedOrder.id,
                "buyerId"     to savedOrder.buyerId,
                "totalAmount" to savedOrder.totalAmount,
                "orderLines"  to savedOrder.orderLines.map { line ->
                    mapOf(
                        "productId"            to line.productId,
                        "productNameSnapshot"  to line.productNameSnapshot,
                        "unitPrice"            to line.unitPrice,
                        "quantity"             to line.quantity
                    )
                },
            )
        )

        outboxRepository.save(
            OutboxEvent.of(
                eventType     = "order.created.v1",
                aggregateType = "ORDER",
                aggregateId   = savedOrder.id!!.toString(),
                payload       = payload
            )
        )

        return savedOrder.id!!
    }
}