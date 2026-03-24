package orderservice.orderservice.adapter.web

import jakarta.validation.Valid
import orderservice.orderservice.adapter.redis.RateLimiter
import orderservice.orderservice.adapter.web.dto.ConfirmVoiceOrderHttpRequest
import orderservice.orderservice.adapter.web.dto.VoiceOrderHttpRequest
import orderservice.orderservice.application.VoiceOrderService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * 음성 주문 API 어댑터.
 *
 * POST /api/orders/voice          → Redis에 임시 저장 (TTL 10분)
 * POST /api/orders/voice/confirm  → 확정 → Order 생성
 * DELETE /api/orders/voice/{userId} → 음성 주문 취소
 *
 * 처리율 제한: 저장 요청 시 RateLimiter(토큰 버킷) 통과 필수.
 * 초과 시 429 Too Many Requests 반환.
 */
@RestController
@RequestMapping("/api/orders/voice")
class VoiceOrderApi(
    private val voiceOrderService: VoiceOrderService,
    private val rateLimiter: RateLimiter,
) {

    @PostMapping
    fun save(
        @RequestBody @Valid request: VoiceOrderHttpRequest
    ): ResponseEntity<Unit> {
        if (!rateLimiter.isAllowed(request.buyerId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        }
        voiceOrderService.save(request.buyerId, request.toDomain())
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/confirm")
    fun confirm(
        @RequestBody @Valid request: ConfirmVoiceOrderHttpRequest
    ): ResponseEntity<Map<String, Long>> {
        val orderId = voiceOrderService.confirm(request.userId)
        return ResponseEntity
            .created(URI.create("/api/orders/$orderId"))
            .body(mapOf("orderId" to orderId))
    }

    @DeleteMapping("/{userId}")
    fun cancel(@PathVariable userId: Long): ResponseEntity<Unit> {
        voiceOrderService.cancel(userId)
        return ResponseEntity.noContent().build()
    }
}