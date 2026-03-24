package orderservice.orderservice.domain.order

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * Order Aggregate Root.
 *
 * 설계 원칙:
 * - private constructor + companion object factory → 팩토리 메서드로만 생성 강제
 * - @PersistenceCreator → Spring Data JDBC가 DB에서 읽을 때 reflection으로 생성
 * - Spring Data JDBC 전용: @Table, @Id, @MappedCollection (JPA 어노테이션 금지)
 * - deliveryId: Delivery Aggregate는 ID로만 참조 (직접 참조 금지)
 *
 * AllMart 문제 해결:
 * - Order ↔ Delivery 강결합(@ManyToOne) → ID 참조로 분리
 */
@Table("orders")
class Order @PersistenceCreator private constructor(
    @Id val id: Long? = null,
    val buyerId: Long,
    val totalAmount: Long,
    var status: OrderStatus = OrderStatus.PENDING_PAYMENT,
    @MappedCollection(idColumn = "order_id")
    val orderLines: Set<OrderLine> = emptySet(),
    var deliveryId: Long? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var confirmedAt: LocalDateTime? = null,
    var cancelledAt: LocalDateTime? = null,
) {

    companion object {
        fun create(buyerId: Long, orderLines: List<OrderLine>): Order {
            require(buyerId > 0) { "buyerId는 양수여야 합니다. buyerId=$buyerId" }
            require(orderLines.isNotEmpty()) { "주문 항목은 최소 1개 이상이어야 합니다." }

            val totalAmount = orderLines.sumOf { it.unitPrice * it.quantity }

            return Order(
                buyerId = buyerId,
                totalAmount = totalAmount,
                orderLines = orderLines.toSet()
            )
        }
    }

    fun markAsPaid(): Order {
        check(status.canTransitionTo(OrderStatus.PAID)) {
            "PAID로 전환 불가 — 현재 상태: $status"
        }
        status = OrderStatus.PAID
        confirmedAt = LocalDateTime.now()
        return this
    }

    fun markAsPreparing(): Order {
        check(status.canTransitionTo(OrderStatus.PREPARING)) {
            "PREPARING으로 전환 불가 — 현재 상태: $status"
        }
        status = OrderStatus.PREPARING
        return this
    }

    fun markAsDispatched(deliveryId: Long): Order {
        check(status.canTransitionTo(OrderStatus.DISPATCHED)) {
            "DISPATCHED로 전환 불가 — 현재 상태: $status"
        }
        status = OrderStatus.DISPATCHED
        this.deliveryId = deliveryId
        return this
    }

    fun markAsDelivered(): Order {
        check(status.canTransitionTo(OrderStatus.DELIVERED)) {
            "DELIVERED로 전환 불가 — 현재 상태: $status"
        }
        status = OrderStatus.DELIVERED
        return this
    }

    fun cancel(): Order {
        check(status.canTransitionTo(OrderStatus.CANCELLED)) {
            "취소 불가 — 현재 상태: $status"
        }
        status = OrderStatus.CANCELLED
        cancelledAt = LocalDateTime.now()
        return this
    }
}