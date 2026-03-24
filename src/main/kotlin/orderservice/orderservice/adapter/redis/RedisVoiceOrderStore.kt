package orderservice.orderservice.adapter.redis

import orderservice.orderservice.domain.order.OrderCreateRequest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/**
 * Redis TTL 기반 음성 주문 임시 저장.
 *
 * AllMart 문제 해결:
 * - TemporaryOrderScheduler: cron 오류 + 전체 메모리 로드 + 중복 처리 위험 완전 제거
 * - Redis TTL 10분 → 만료 시 자동 폐기, DB 쓰레기 없음
 *
 * Key: voice:order:{userId}, TTL: 10분
 */
@Component
class RedisVoiceOrderStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val KEY_PREFIX = "voice:order:"
        private val TTL = Duration.ofMinutes(10)
    }

    fun save(userId: Long, request: OrderCreateRequest) {
        redisTemplate.opsForValue().set(
            "$KEY_PREFIX$userId",
            objectMapper.writeValueAsString(request),
            TTL
        )
    }

    fun load(userId: Long): OrderCreateRequest? {
        val json = redisTemplate.opsForValue().get("$KEY_PREFIX$userId") ?: return null
        return objectMapper.readValue(json, OrderCreateRequest::class.java)
    }

    fun delete(userId: Long) {
        redisTemplate.delete("$KEY_PREFIX$userId")
    }
}