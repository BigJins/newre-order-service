package orderservice.orderservice.adapter.web

import com.ninjasquad.springmockk.MockkBean
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper

/**
 * 통합 테스트 — API 요청부터 DB 저장까지 전체 흐름 검증.
 *
 * 테스트 전략:
 * - @SpringBootTest       : 전체 스프링 컨텍스트 로드 (실제 Service, Repository 동작)
 * - MockMvcBuilders       : @AutoConfigureMockMvc 없이 WebApplicationContext로 MockMvc 수동 생성
 * - @Transactional        : 각 테스트 후 DB 자동 롤백 → 테스트 간 완전 격리
 * - DB                    : H2 인메모리 (application-test.yaml + schema.sql)
 * - Redis                 : @MockkBean(relaxed=true) → 실제 연결 없음, stub 불필요
 * - Kafka                 : @EmbeddedKafka → 인메모리 브로커
 *
 * 검증 흐름:
 * MockMvc → OrderApi → OrderCreateService → JdbcOrderRepository (H2)
 *                                         → JdbcOutboxRepository (H2)
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = ["spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}"])
class OrderApiIntegrationTest {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    lateinit var objectMapper: ObjectMapper

    // Redis 빈을 relaxed mock으로 교체 — Order API는 Redis를 직접 사용하지 않으므로 stub 불필요
    @MockkBean(relaxed = true)
    lateinit var stringRedisTemplate: StringRedisTemplate

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setupMockMvc() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()
    }

    // ─── POST /api/orders ─────────────────────────────────────────────────────

    @Test
    fun `POST api_orders — 유효한 요청 시 201, Location 헤더, orderId 반환`() {
        mockMvc.perform(
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOrderJson())
        )
            .andExpect(status().isCreated)
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.orderId").isNumber)
    }

    @Test
    fun `POST api_orders — buyerId 누락 시 400 반환`() {
        mockMvc.perform(
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "orderLines": [
                        {"productId": 10, "productNameSnapshot": "아메리카노", "unitPrice": 4500, "quantity": 2}
                      ]
                    }
                    """.trimIndent()
                )
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST api_orders — orderLines 빈 배열 시 400 반환`() {
        mockMvc.perform(
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"buyerId": 1, "orderLines": []}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST api_orders — unitPrice가 0이면 400 반환`() {
        mockMvc.perform(
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "buyerId": 1,
                      "orderLines": [
                        {"productId": 10, "productNameSnapshot": "상품A", "unitPrice": 0, "quantity": 1}
                      ]
                    }
                    """.trimIndent()
                )
        ).andExpect(status().isBadRequest)
    }

    // ─── GET /api/orders/{id} ─────────────────────────────────────────────────

    @Test
    fun `POST 후 GET — 저장된 주문을 조회하면 200과 주문 상세 정보 반환`() {
        // given: POST로 주문 생성 (@Transactional이므로 같은 트랜잭션에서 visible)
        val postResult = mockMvc.perform(
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOrderJson())
        ).andReturn()

        val orderId = objectMapper
            .readTree(postResult.response.contentAsString)["orderId"]
            .asLong()

        // when & then: 동일 트랜잭션 내에서 조회 가능
        mockMvc.perform(get("/api/orders/$orderId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.buyerId").value(1))
            .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.totalAmount").value(9000))
            .andExpect(jsonPath("$.orderLines[0].productNameSnapshot").value("아메리카노"))
    }

    @Test
    fun `GET api_orders_id — 존재하지 않는 주문 조회 시 404 반환`() {
        mockMvc.perform(get("/api/orders/99999"))
            .andExpect(status().isNotFound)
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private fun validOrderJson() = """
        {
          "buyerId": 1,
          "orderLines": [
            {
              "productId": 10,
              "productNameSnapshot": "아메리카노",
              "unitPrice": 4500,
              "quantity": 2
            }
          ]
        }
    """.trimIndent()
}