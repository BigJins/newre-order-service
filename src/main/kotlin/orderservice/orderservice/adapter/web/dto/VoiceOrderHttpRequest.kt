package orderservice.orderservice.adapter.web.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import orderservice.orderservice.domain.order.OrderCreateRequest

data class VoiceOrderHttpRequest(
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

data class ConfirmVoiceOrderHttpRequest(
    @field:Positive(message = "userId는 양수여야 합니다.")
    val userId: Long,
)