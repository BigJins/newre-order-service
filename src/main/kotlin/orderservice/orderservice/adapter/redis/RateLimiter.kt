package orderservice.orderservice.adapter.redis

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

/**
 * Redis 토큰 버킷 처리율 제한.
 *
 * AllMart 문제 해결:
 * - TemporaryOrderScheduler → Redis TTL 도입 후, 같은 Redis 인프라로 처리율 제한까지 확장
 * - 음성 주문 API 남용(동일 사용자 반복 호출) → 토큰 버킷으로 차단
 *
 * 알고리즘: 토큰 버킷 (Token Bucket)
 * - 버킷 용량: 5회 (burst 허용)
 * - 리필 속도: 초당 1토큰
 * - Lua 스크립트로 Redis 내 원자적 실행 → 분산 환경에서도 race condition 없음
 *
 * Key: rate_limit:order:{userId}, TTL: 자동 만료 (버킷 소진 후 리필 시간)
 */
@Component
class RateLimiter(
    private val redisTemplate: StringRedisTemplate
) {
    companion object {
        private const val KEY_PREFIX = "rate_limit:order:"
        private const val MAX_TOKENS = 5L       // 버킷 최대 용량
        private const val REFILL_RATE = 1L      // 초당 리필 토큰 수

        // 토큰 버킷 Lua 스크립트 (원자적 실행)
        // KEYS[1]: rate limit key
        // ARGV[1]: 버킷 최대 용량, ARGV[2]: 초당 리필량, ARGV[3]: 현재 시각(ms)
        private val SCRIPT = DefaultRedisScript<Long>().apply {
            setScriptText(
                """
                local key = KEYS[1]
                local max_tokens = tonumber(ARGV[1])
                local refill_rate = tonumber(ARGV[2])
                local now = tonumber(ARGV[3])

                local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
                local tokens = tonumber(bucket[1])
                local last_refill = tonumber(bucket[2])

                if tokens == nil then
                    tokens = max_tokens
                    last_refill = now
                end

                local elapsed = math.max(0, now - last_refill)
                local refill = math.floor(elapsed * refill_rate / 1000)
                tokens = math.min(max_tokens, tokens + refill)
                local last_refill_updated = (refill > 0) and now or last_refill

                if tokens >= 1 then
                    tokens = tokens - 1
                    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill_updated)
                    redis.call('EXPIRE', key, math.ceil(max_tokens / refill_rate) + 1)
                    return 1
                else
                    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill_updated)
                    redis.call('EXPIRE', key, math.ceil(max_tokens / refill_rate) + 1)
                    return 0
                end
                """.trimIndent()
            )
            resultType = Long::class.javaObjectType
        }
    }

    fun isAllowed(userId: Long): Boolean {
        val result = redisTemplate.execute(
            SCRIPT,
            listOf("$KEY_PREFIX$userId"),
            MAX_TOKENS.toString(),
            REFILL_RATE.toString(),
            System.currentTimeMillis().toString()
        )
        return result == 1L
    }
}
