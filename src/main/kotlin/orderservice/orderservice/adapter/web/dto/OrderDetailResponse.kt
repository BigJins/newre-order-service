package orderservice.orderservice.adapter.web.dto

import orderservice.orderservice.domain.order.Order
import java.time.LocalDateTime

data class OrderDetailResponse(
    val orderId: Long,
    val buyerId: Long,
    val status: String,
    val totalAmount: Long,
    val orderLines: List<OrderLineResponse>,
    val createdAt: LocalDateTime,
    val confirmedAt: LocalDateTime?,
    val cancelledAt: LocalDateTime?,
) {
    companion object {
        fun from(order: Order): OrderDetailResponse = OrderDetailResponse(
            orderId      = order.id!!,
            buyerId      = order.buyerId,
            status       = order.status.name,
            totalAmount  = order.totalAmount,
            orderLines   = order.orderLines.map { OrderLineResponse.from(it) },
            createdAt    = order.createdAt,
            confirmedAt  = order.confirmedAt,
            cancelledAt  = order.cancelledAt
        )
    }
}

data class OrderLineResponse(
    val productId: Long,
    val productNameSnapshot: String,
    val unitPrice: Long,
    val quantity: Int
) {
    companion object {
        fun from(line: orderservice.orderservice.domain.order.OrderLine): OrderLineResponse =
            OrderLineResponse(
                productId = line.productId,
                productNameSnapshot = line.productNameSnapshot,
                unitPrice = line.unitPrice,
                quantity = line.quantity,
            )
    }
}