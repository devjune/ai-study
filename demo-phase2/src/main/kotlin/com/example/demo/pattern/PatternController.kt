package com.example.demo.pattern

import com.example.demo.support.Order
import com.example.demo.support.OrderService
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.*

/**
 * 패턴 하나를 실행하고 **모델 호출 타임라인**을 돌려준다.
 *
 * 화면의 막대 하나가 모델 호출 한 번이다. 패턴마다 그 개수와 순서가 다르다는 것이
 * 이 데모가 보여주려는 전부다.
 */
@RestController
@RequestMapping("/api")
class PatternController(
    private val runners: List<PatternRunner>,
    private val builder: ChatClient.Builder,
    private val orders: OrderService,
    @Value("\${demo.models}") private val models: List<String>,
) {
    private val byChapter = runners.associateBy { it.spec.chapter }

    @GetMapping("/patterns")
    fun patterns(): List<PatternSpec> = runners.map { it.spec }.sortedBy { it.chapter }

    @GetMapping("/models")
    fun models(): List<String> = models

    @GetMapping("/orders")
    fun orders(): List<Order> = orders.all()

    @PostMapping("/patterns/{chapter}/run")
    fun run(@PathVariable chapter: Int, @RequestBody req: RunRequest): RunResult {
        val runner = byChapter[chapter]
            ?: return RunResult(false, "${chapter}장 러너가 없습니다", emptyList(), 0)
        val ctx = RunContext(builder, req.input?.ifBlank { null } ?: runner.spec.defaultInput, req.model ?: runner.spec.model, runner.spec.maxTokens)
        val started = System.nanoTime()
        return runCatching { runner.run(ctx) }
            .map { it.copy(elapsedMs = if (it.elapsedMs > 0) it.elapsedMs else (System.nanoTime() - started) / 1_000_000) }
            .getOrElse {
                ctx.result("${it::class.simpleName}: ${it.message?.take(300)}", ok = false,
                    elapsedMs = (System.nanoTime() - started) / 1_000_000)
            }
    }

    /** 13장 사람 개입 — 승인 버튼이 부르는 자리. **LLM 이 관여하지 않는다.** */
    @PostMapping("/approve")
    fun approve(@RequestBody req: ApproveRequest): ApproveReply {
        orders.cancelBlockedReason(req.orderId)?.let { return ApproveReply(false, it, orders.all()) }
        val cancelled = orders.cancel(req.orderId)
            ?: return ApproveReply(false, "주문번호 ${req.orderId} 를 찾을 수 없습니다.", orders.all())
        return ApproveReply(true, "주문 ${cancelled.id} 이 취소되었습니다.", orders.all())
    }

    /** 시연을 반복하려면 주문 상태를 되돌려야 한다. */
    @PostMapping("/reset")
    fun reset(): List<Order> = orders.reset()
}

data class RunRequest(val input: String?, val model: String?)
data class ApproveRequest(val orderId: String)
data class ApproveReply(val ok: Boolean, val message: String, val orders: List<Order>)
