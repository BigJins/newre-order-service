package orderservice.orderservice.application.provided

import orderservice.orderservice.domain.order.OrderCreateRequest

/**
 * 인바운드 포트 — 주문 생성.
 * Web 어댑터가 이 인터페이스만 의존하므로 구현체(OrderCreateService)를 교체해도 API 코드 변경 없음.
 */
interface OrderCreator {
    fun create(request: OrderCreateRequest): Long
}