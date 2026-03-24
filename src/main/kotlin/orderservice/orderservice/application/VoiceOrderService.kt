package orderservice.orderservice.application

import orderservice.orderservice.adapter.redis.RedisVoiceOrderStore
import orderservice.orderservice.application.provided.OrderCreator
import orderservice.orderservice.domain.order.OrderCreateRequest
import org.springframework.stereotype.Service

/**
 * 음성 주문 처리 유스케이스.
 *
 * AllMart 문제 해결:
 * - TemporaryOrderScheduler: cron 오류 + findByStatus(PENDING) 전체 메모리 로드 + 중복 처리 위험
 * - 대체: Redis TTL(10분) → 만료 시 자동 폐기, Scheduler 불필요, DB 쓰레기 없음
 */
@Service
class VoiceOrderService(
    private val voiceOrderStore: RedisVoiceOrderStore,
    private val orderCreator: OrderCreator
) {
    fun save(userId: Long, request: OrderCreateRequest) {
        voiceOrderStore.save(userId, request)
    }

    fun confirm(userId: Long): Long {
        val request = voiceOrderStore.load(userId)
            ?: throw IllegalStateException("음성 주문이 존재하지 않거나 만료되었습니다. userId=$userId")
        val orderId = orderCreator.create(request)
        voiceOrderStore.delete(userId)
        return orderId
    }

    fun cancel(userId: Long) {
        voiceOrderStore.delete(userId)
    }
}