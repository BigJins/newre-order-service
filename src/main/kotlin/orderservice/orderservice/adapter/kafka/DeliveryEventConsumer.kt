package orderservice.orderservice.adapter.kafka

import orderservice.orderservice.application.provided.OrderStatusUpdater
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Delivery Service → Order Service 이벤트 수신.
 *
 * Debezium EventRouter: delivery_db.outbox_event.payload → Kafka 메시지 값으로 전달.
 * ack-mode: record → 처리 완료 후 수동 커밋 (메시지 유실 방지)
 *
 * 흐름:
 *   delivery.assigned.v1  → Order.markAsDispatched(deliveryId) + outbox INSERT
 *   delivery.completed.v1 → Order.markAsDelivered()             + outbox INSERT
 *   delivery.cancelled.v1 → Order.cancel()                      + outbox INSERT
 */
@Component
class DeliveryEventConsumer(
    private val orderStatusUpdater: OrderStatusUpdater,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(topics = ["delivery.assigned.v1"], groupId = "order-service")
    fun handleDeliveryAssigned(message: String) {
        val payload = objectMapper.readTree(message)
        val orderId    = payload["orderId"].asLong()
        val deliveryId = payload["deliveryId"].asLong()
        orderStatusUpdater.markAsDispatched(orderId, deliveryId)
    }

    @KafkaListener(topics = ["delivery.completed.v1"], groupId = "order-service")
    fun handleDeliveryCompleted(message: String) {
        val payload = objectMapper.readTree(message)
        val orderId = payload["orderId"].asLong()
        orderStatusUpdater.markAsDelivered(orderId)
    }

    @KafkaListener(topics = ["delivery.cancelled.v1"], groupId = "order-service")
    fun handleDeliveryCancelled(message: String) {
        val payload = objectMapper.readTree(message)
        val orderId = payload["orderId"].asLong()
        orderStatusUpdater.cancel(orderId)
    }
}