package orderservice.orderservice.domain.order

/**
 * 도메인 입력 객체 — 주문 생성 요청.
 * 웹 DTO와 분리하여 도메인 로직이 HTTP 구조에 의존하지 않도록 한다.
 */
data class OrderCreateRequest(
    val buyerId: Long,
    val orderLines: List<OrderLineCreateRequest>,
)

data class OrderLineCreateRequest(
    val productId: Long,
    val productNameSnapshot: String,
    val unitPrice: Long,
    val quantity: Int
)