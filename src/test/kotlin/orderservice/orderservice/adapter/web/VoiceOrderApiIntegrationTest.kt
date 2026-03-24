package orderservice.orderservice.adapter.web

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import orderservice.orderservice.adapter.redis.RateLimiter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.MediaType
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.time.Duration

/**
 * 통합 테스트 — 음성 주문 API 전체 흐름 검증.
 *
 * 테스트 전략:
 * - @SpringBootTest       : 전체 스프링 컨텍스트 로드
 * - MockMvcBuilders       : WebApplicationContext로 MockMvc 수동 생성
 * - @Transactional        : 각 테스트 후 DB 롤백
 * - Redis                 : StringRedisTemplate을 @MockkBean으로 교체
 *   → confirm 테스트: mock이 OrderCreateRequest JSON 반환 → 실제 Order가 H2에 저장됨
 * - RateLimiter           : @MockkBean으로 교체 — 기본은 허용(true), 429 테스트만 false로 오버라이드
 * - Kafka                 : @EmbeddedKafka
 *
 * 검증 흐름 (confirm):
 * MockMvc → VoiceOrderApi → VoiceOrderService
 *   → RedisVoiceOrderStore.load() (mock)
 *   → OrderCreateService → JdbcOrderRepository (H2) + JdbcOutboxRepository (H2)
 *   → RedisVoiceOrderStore.delete() (mock)
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = ["spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}"])
class VoiceOrderApiIntegrationTest {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @MockkBean
    lateinit var stringRedisTemplate: StringRedisTemplate

    @MockkBean
    lateinit var rateLimiter: RateLimiter

    private lateinit var valueOps: ValueOperations<String, String>
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        valueOps = mockk()
        every { stringRedisTemplate.opsForValue() } returns valueOps
        every { valueOps.set(any<String>(), any<String>(), any<Duration>()) } returns Unit
        every { valueOps.get(any<String>()) } returns null
        every { stringRedisTemplate.delete(any<String>()) } returns true

        // 기본: 처리율 제한 통과
        every { rateLimiter.isAllowed(any()) } returns true
    }

    // ─── POST /api/orders/voice ───────────────────────────────────────────────

    @Test
    fun `POST api_orders_voice — 유효한 요청 시 Redis에 저장 후 202 반환`() {
        mockMvc.perform(
            post("/api/orders/voice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validVoiceOrderJson())
        ).andExpect(status().isAccepted)

        verify(exactly = 1) { valueOps.set(any<String>(), any<String>(), any<Duration>()) }
    }

    @Test
    fun `POST api_orders_voice — 처리율 초과 시 429 반환`() {
        every { rateLimiter.isAllowed(any()) } returns false

        mockMvc.perform(
            post("/api/orders/voice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validVoiceOrderJson())
        ).andExpect(status().isTooManyRequests)

        // Redis 저장은 호출되지 않아야 한다
        verify(exactly = 0) { valueOps.set(any<String>(), any<String>(), any<Duration>()) }
    }

    @Test
    fun `POST api_orders_voice — buyerId 누락 시 400 반환`() {
        mockMvc.perform(
            post("/api/orders/voice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "orderLines": [
                        {"productId":1,"productNameSnapshot":"카페라떼","unitPrice":5000,"quantity":1}
                      ]
                    }
                    """.trimIndent()
                )
        ).andExpect(status().isBadRequest)
    }

    // ─── POST /api/orders/voice/confirm ──────────────────────────────────────

    @Test
    fun `POST api_orders_voice_confirm — Redis에 음성 주문 존재 시 실제 Order 생성 후 201 반환`() {
        every { valueOps.get("voice:order:1") } returns """
            {
              "buyerId": 1,
              "orderLines": [
                {"productId": 10, "productNameSnapshot": "카페라떼", "unitPrice": 5000, "quantity": 1}
              ]
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/orders/voice/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId": 1}""")
        )
            .andExpect(status().isCreated)
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.orderId").isNumber)

        verify(exactly = 1) { stringRedisTemplate.delete("voice:order:1") }
    }

    @Test
    fun `POST api_orders_voice_confirm — Redis에 데이터 없음 (TTL 만료) 시 500 반환`() {
        mockMvc.perform(
            post("/api/orders/voice/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId": 1}""")
        ).andExpect(status().is5xxServerError)
    }

    // ─── DELETE /api/orders/voice/{userId} ───────────────────────────────────

    @Test
    fun `DELETE api_orders_voice_userId — 음성 주문 취소 시 Redis 삭제 후 204 반환`() {
        mockMvc.perform(delete("/api/orders/voice/1"))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { stringRedisTemplate.delete("voice:order:1") }
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private fun validVoiceOrderJson() = """
        {
          "buyerId": 1,
          "orderLines": [
            {"productId": 10, "productNameSnapshot": "카페라떼", "unitPrice": 5000, "quantity": 1}
          ]
        }
    """.trimIndent()
}
