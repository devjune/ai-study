package com.example.demo.pattern.ch05

import com.example.demo.pattern.*
import com.example.demo.support.OrderService
import org.springframework.stereotype.Component

@Component
class ToolUse(private val orderService: OrderService) : PatternRunner {
    override val spec = PatternSpec(
        5, "도구 사용", "Tool Use",
        summary = "모델의 지식은 학습 시점에 멈춰 있습니다. 도구 사용은 모델이 필요할 때 외부 함수를 부르게 합니다.",
        code = """
            class SupportTools(private val orderService: OrderService) {

                // @Tool      — 이 함수를 모델이 부를 수 있는 도구로 등록한다
                // description — 모델이 읽는 유일한 설명. 언제 부를지 여기서 판단한다
                @Tool(description = "주문 번호로 주문 상태와 배송 정보를 조회한다")
                fun lookupOrder(
                    // @ToolParam — 인자 설명. 스키마의 description 으로 들어간다
                    @ToolParam(description = "주문 번호. 예: A-1024") orderId: String,
                ): String = orderService.find(orderId)?.summary() ?: "주문번호 ${'$'}orderId 를 찾을 수 없습니다."
            }

            chatClient.prompt()                   // 호출 하나를 조립하기 시작한다
                .user(질문)
                .tools(SupportTools(orderService))      // @Tool 이 붙은 객체를 통째로 넘긴다
                .call()                           // 동기 호출 (토큰을 흘리려면 .stream())
                .content()                        // 응답을 문자열로. 타입으로 받으려면 .entity()
        """.trimIndent(),
        diagram = """
            flowchart LR
                Q[질문] --> M1((모델))
                M1 -->|이 함수를 불러 달라| F["@Tool 실행"]
                F -->|결과| M2((모델))
                M2 --> A[최종 답]
        """.trimIndent(),
        defaultInput = "A-1024 지금 어디쯤 왔어?",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        val tools = SupportTools(orderService)
        val answer = ctx.step("도구 왕복") { it.user(ctx.input).tools(tools).call().content() }
        ctx.note(tools.callSummary())
        if (tools.calls.isNotEmpty())
            ctx.note("`.call()` 한 번인데 위 타임라인은 두 칸입니다. 프레임워크가 왕복을 돌았기 때문입니다")
        return ctx.result(answer)
    }
}
