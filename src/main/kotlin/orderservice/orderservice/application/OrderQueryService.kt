package orderservice.orderservice.application

import orderservice.orderservice.application.persistence.OrderRepository
import orderservice.orderservice.application.provided.OrderFinder
import orderservice.orderservice.domain.order.Order
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class OrderQueryService(
    private val orderRepository: OrderRepository,
) : OrderFinder {

    override fun findById(id: Long): Order? = orderRepository.findById(id).orElse(null)
}