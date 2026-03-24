package orderservice.orderservice.adapter.web.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import orderservice.orderservice.domain.order.OrderCreateRequest
import orderservice.orderservice.domain.order.OrderLineCreateRequest

data class CreateOrderHttpRequest(
    @field:Positive(message = "buyerId는 양수여야 합니다.")
    val buyerId: Long,

    @field:NotEmpty(message = "주문 항목은 최소 1개 이상이어야 합니다.")
    @field:Valid
    val orderLines: List<OrderLineHttpRequest>
) {
    fun toDomain(): OrderCreateRequest = OrderCreateRequest(
        buyerId = buyerId,
        orderLines = orderLines.map { it.toDomain() },
    )
}

data class OrderLineHttpRequest(
    @field:Positive(message = "productId는 양수여야 합니다.")
    val productId: Long,

    @field:NotBlank(message = "상품명은 비어있을 수 없습니다.")
    val productNameSnapshot: String,

    @field:Min(value = 1, message = "단가는 1 이상이어야 합니다.")
    val unitPrice: Long,

    @field:Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    val quantity: Int
) {
    fun toDomain(): OrderLineCreateRequest = OrderLineCreateRequest(
        productId = productId,
        productNameSnapshot = productNameSnapshot,
        unitPrice = unitPrice,
        quantity = quantity,
    )
}