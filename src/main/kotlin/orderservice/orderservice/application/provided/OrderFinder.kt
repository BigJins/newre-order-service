package orderservice.orderservice.application.provided

import orderservice.orderservice.domain.order.Order

/**
 * 인바운드 포트 — 주문 조회.
 */
interface OrderFinder {
    fun findById(id: Long): Order?
}