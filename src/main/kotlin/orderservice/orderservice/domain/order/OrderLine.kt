package orderservice.orderservice.domain.order

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

/**
 * Order Aggregate의 하위 값 객체.
 * - Order 없이 단독 존재 불가
 * - productNameSnapshot, unitPrice: 주문 시점 스냅샷 → 이후 상품 변경/삭제 영향 없음
 *
 * AllMart 문제 해결:
 * - Product를 직접 참조(ManyToOne) → 상품 삭제 시 주문 이력 깨짐
 * - 스냅샷 저장으로 주문 이력 불변 보장
 */
@Table("order_lines")
data class OrderLine(
    @Id val id: Long? = null,
    val productId: Long,
    val productNameSnapshot: String,
    val unitPrice: Long,
    val quantity: Int
)