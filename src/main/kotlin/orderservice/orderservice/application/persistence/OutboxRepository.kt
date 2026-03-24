package orderservice.orderservice.application.persistence

import orderservice.orderservice.domain.outbox.OutboxEvent
import org.springframework.data.repository.CrudRepository

interface OutboxRepository : CrudRepository<OutboxEvent, Long>
