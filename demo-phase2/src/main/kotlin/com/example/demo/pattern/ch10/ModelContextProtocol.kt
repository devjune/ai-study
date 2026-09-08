package com.example.demo.pattern.ch10

import com.example.demo.pattern.*
import org.springframework.stereotype.Component

@Component
class ModelContextProtocol : PatternRunner {
    override val spec = PatternSpec(
        10, "MCP", "Model Context Protocol",
        summary = "모델과 외부 도구를 이을 때마다 다른 방식으로 붙이면 재사용이 안 됩니다. 표준 프로토콜로 도구를 노출합니다.",
        code = """
            @Component
            class McpTools(private val orderService: OrderService) {
                // @McpTool — MCP 프로토콜로 노출된다. 스키마는 시그니처에서 자동 생성
                @McpTool(name = "lookupOrder", description = "주문 번호로 주문 상태와 배송 정보를 조회한다")
                fun lookupOrder(
                    @McpToolParam(description = "주문 번호. 예: A-1024") orderId: String,
                ): String = orderService.find(orderId)?.summary() ?: "주문번호 ${'$'}orderId 를 찾을 수 없습니다."
            }
        """.trimIndent(),
        diagram = """
            flowchart LR
                C[다른 언어로 짠 에이전트] -.tools/list.-> S["내 앱 · @McpTool"]
                C -.tools/call.-> S
                S --> R[함수 실행]
        """.trimIndent(),
        defaultInput = "A-1024",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        ctx.note("이 앱이 곧 MCP 서버입니다. 아래는 자기 `/mcp` 엔드포인트를 실제로 부른 결과입니다")
        val list = mcpCall("tools/list")
        ctx.note("tools/list — 선언한 함수가 스키마로 나옵니다. 개발자가 JSON 을 쓴 적은 없습니다")
        val invoke = mcpCall("tools/call", """{"name":"lookupOrder","arguments":{"orderId":"${ctx.input}"}}""")
        ctx.note("tools/call — 프로토콜로 실제 실행까지")
        return ctx.result("$list\n\n─────\n\n$invoke")
    }
}
