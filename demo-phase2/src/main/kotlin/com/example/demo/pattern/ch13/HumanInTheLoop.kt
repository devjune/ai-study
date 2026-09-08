package com.example.demo.pattern.ch13

import com.example.demo.pattern.*
import com.example.demo.support.OrderService
import org.springframework.stereotype.Component

@Component
class HumanInTheLoop(private val orderService: OrderService) : PatternRunner {
    override val spec = PatternSpec(
        13, "사람 개입", "Human-in-the-Loop",
        summary = "실수의 대가가 큰 일을 모델에게 맡기는 것은 위험합니다. 결정적인 지점에 사람의 판단을 끼웁니다.",
        code = """
            // description 에 "실제로 취소하지 않는다"고 적어 둔다. 모델이 읽는 계약이다
            // 승인을 기다리는 주문 번호. 화면이 이 값을 보고 승인 버튼을 단다
            var pendingCancel: String? = null

            @Tool(description = "주문 취소를 요청한다. 실제로 취소하지 않고 고객 승인을 받는 단계다.")
            fun requestCancellation(orderId: String): String {
                orderService.cancelBlockedReason(orderId)?.let { return "취소할 수 없습니다 — ${'$'}it" }
                pendingCancel = orderService.find(orderId)!!.id      // 요청만 기록. 상태는 건드리지 않는다
                return "주문 ${'$'}pendingCancel 은 취소 가능합니다. 고객이 승인 버튼을 눌러야 실제로 취소됩니다."
            }

            // 평범한 컨트롤러. ChatClient 도 Advisor 도 등장하지 않는다
            @PostMapping("/approve")
            fun approve(@RequestBody req: ApproveRequest): ApproveReply {
                orderService.cancel(req.orderId)                   // 여기서 비로소 상태가 바뀐다
                return ApproveReply(true, "취소되었습니다", orderService.all())
            }
        """.trimIndent(),
        diagram = """
            flowchart LR
                U[고객] --> M1((모델))
                M1 -->|취소를 요청하라| T[requestCancellation<br/>요청만 기록]
                T -->|취소 가능하다| M2((모델))
                M2 --> W[승인 대기]
                subgraph H [사람이 결정하는 구간 — 모델이 없다]
                    W -.버튼을 누른다.-> A[POST /approve]
                    A --> D[(주문 취소됨)]
                end
                style H fill:#8884,stroke:#888,stroke-dasharray:4 3
        """.trimIndent(),
        defaultInput = "A-1024 취소해줘",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        // 시연을 몇 번이고 반복할 수 있게, 승인으로 바뀐 상태를 실행마다 되돌린다
        orderService.reset()

        val before = orderService.find("A-1024")?.status
        val tools = SupportTools(orderService)
        val answer = ctx.step("도구 왕복") { it.user(ctx.input).tools(tools).call().content() }
        val after = orderService.find("A-1024")?.status

        ctx.note(tools.callSummary())
        ctx.note("주문 상태 — 호출 전 `$before` · 호출 후 `$after` (바뀌지 않았습니다)")
        tools.pendingCancel?.let { ctx.note("승인 대기 — $it. 화면의 [승인] 버튼이 눌려야 실제로 바뀝니다") }
        return ctx.result(answer, pendingCancel = tools.pendingCancel)
    }
}
