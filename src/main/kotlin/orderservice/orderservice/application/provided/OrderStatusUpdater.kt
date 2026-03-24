package orderservice.orderservice.application.provided

/**
 * 외부 이벤트(Delivery Service)에 의한 Order 상태 변경 인바운드 포트.
 *
 * Kafka Consumer → 이 인터페이스 → OrderStatusUpdateService
 */
interface OrderStatusUpdater {
    fun markAsDispatched(orderId: Long, deliveryId: Long)
    fun markAsDelivered(orderId: Long)
    fun cancel(orderId: Long)
}