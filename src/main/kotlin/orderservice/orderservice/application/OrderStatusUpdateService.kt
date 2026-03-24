package orderservice.orderservice.application

import orderservice.orderservice.application.persistence.OrderRepository
import orderservice.orderservice.application.persistence.OutboxRepository
import orderservice.orderservice.application.provided.OrderStatusUpdater
import orderservice.orderservice.domain.outbox.OutboxEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * Delivery Service 이벤트를 수신해 Order 상태를 갱신.
 *
 * 핵심 불변식: Order 상태 변경 + OutboxEvent INSERT = 같은 트랜잭션.
 * 흐름:
 *   delivery.assigned.v1  → markAsDispatched(deliveryId) → order.dispatched.v1
 *   delivery.completed.v1 → markAsDelivered()             → order.delivered.v1
 *   delivery.cancelled.v1 → cancel()                      → order.cancelled.v1
 */
@Service
@Transactional
class
OrderStatusUpdateService(
    private val orderRepository: OrderRepository,
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
) : OrderStatusUpdater {

    override fun markAsDispatched(orderId: Long, deliveryId: Long) {
        val order = orderRepository.findById(orderId).orElse(null)
            ?: throw NoSuchElementException("주문을 찾을 수 없습니다: $orderId")
        order.markAsDispatched(deliveryId)
        orderRepository.save(order)

        val payload = objectMapper.writeValueAsString(
            mapOf("orderId" to orderId, "deliveryId" to deliveryId, "status" to order.status.name)
        )
        outboxRepository.save(
            OutboxEvent.of(
                eventType     = "order.dispatched.v1",
                aggregateType = "ORDER",
                aggregateId   = orderId.toString(),
                payload       = payload,
            )
        )
    }

    override fun markAsDelivered(orderId: Long) {
        val order = orderRepository.findById(orderId).orElse(null)
            ?: throw NoSuchElementException("주문을 찾을 수 없습니다: $orderId")
        order.markAsDelivered()
        orderRepository.save(order)

        val payload = objectMapper.writeValueAsString(
            mapOf("orderId" to orderId, "status" to order.status.name)
        )
        outboxRepository.save(
            OutboxEvent.of(
                eventType     = "order.delivered.v1",
                aggregateType = "ORDER",
                aggregateId   = orderId.toString(),
                payload       = payload,
            )
        )
    }

    override fun cancel(orderId: Long) {
        val order = orderRepository.findById(orderId).orElse(null)
            ?: throw NoSuchElementException("주문을 찾을 수 없습니다: $orderId")
        order.cancel()
        orderRepository.save(order)

        val payload = objectMapper.writeValueAsString(
            mapOf("orderId" to orderId, "status" to order.status.name)
        )
        outboxRepository.save(
            OutboxEvent.of(
                eventType     = "order.cancelled.v1",
                aggregateType = "ORDER",
                aggregateId   = orderId.toString(),
                payload       = payload,
            )
        )
    }
}