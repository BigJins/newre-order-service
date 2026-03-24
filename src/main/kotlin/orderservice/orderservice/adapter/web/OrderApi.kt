package orderservice.orderservice.adapter.web

import jakarta.validation.Valid
import orderservice.orderservice.adapter.web.dto.CreateOrderHttpRequest
import orderservice.orderservice.adapter.web.dto.OrderDetailResponse
import orderservice.orderservice.application.provided.OrderCreator
import orderservice.orderservice.application.provided.OrderFinder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * 주문 API 어댑터 (인바운드).
 *
 * 의존 방향: OrderApi → OrderCreator (인터페이스) → OrderCreateService (구현체)
 * Controller가 Repository나 Service 구현체를 직접 의존하지 않음 — 헥사고날 아키텍처 준수.
 *
 * AllMart 문제 해결:
 * - Controller → Repository 직접 참조 차단
 * - Entity를 Request/Response로 사용 금지 → 전용 DTO 사용
 */
@RestController
@RequestMapping("/api/orders")
class OrderApi(
    private val orderCreator: OrderCreator,
    private val orderFinder: OrderFinder
) {

    @PostMapping
    fun create(@RequestBody @Valid request: CreateOrderHttpRequest): ResponseEntity<Map<String, Long>> {
        val orderId = orderCreator.create(request.toDomain())
        return ResponseEntity
            .created(URI.create("/api/orders/$orderId"))
            .body(mapOf("orderId" to orderId))
    }

    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<OrderDetailResponse> {
        val order = orderFinder.findById(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(OrderDetailResponse.from(order))
    }
}