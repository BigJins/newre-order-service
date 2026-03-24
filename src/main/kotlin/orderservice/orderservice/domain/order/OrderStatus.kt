package orderservice.orderservice.domain.order

/**
 * 주문 상태 전이 규칙.
 * exhaustive when → 새 상태 추가 시 canTransitionTo 미처리 시 컴파일 에러로 누락 방지.
 *
 * AllMart 문제 해결:
 * - 상태 전이 검증 없이 setter로 직접 변경 → 불가능한 전이가 발생하던 문제 차단
 */
enum class OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PREPARING,
    DISPATCHED,
    DELIVERED,
    CANCELLED;

    fun canTransitionTo(next: OrderStatus): Boolean = when (this) {
        PENDING_PAYMENT -> next == PAID || next == CANCELLED
        PAID            -> next == PREPARING || next == CANCELLED
        PREPARING       -> next == DISPATCHED || next == CANCELLED
        DISPATCHED      -> next == DELIVERED || next == CANCELLED
        DELIVERED       -> false
        CANCELLED       -> false
    }
}