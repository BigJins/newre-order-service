package orderservice.orderservice.application.persistence

import orderservice.orderservice.domain.order.Order
import org.springframework.data.repository.CrudRepository

interface OrderRepository : CrudRepository<Order, Long>
