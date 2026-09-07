package com.example.demo.pattern

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * 10·15장 — 이 애플리케이션 자신이 MCP 서버다.
 * 러너는 자기 `/mcp` 엔드포인트를 실제로 호출해서, 선언이 프로토콜로 어떻게 나가는지 보여준다.
 */
private val mcp = RestClient.create("http://localhost:8080")

private fun call(method: String, params: String = "{}"): String =
    runCatching {
        mcp.post().uri("/mcp")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .body("""{"jsonrpc":"2.0","id":1,"method":"$method","params":$params}""")
            .retrieve().body(String::class.java).orEmpty()
    }.getOrElse { "호출 실패: ${it.message}" }

@Component
class ModelContextProtocol : PatternRunner {
    override val spec = PatternSpec(
        10, "MCP", "Model Context Protocol",
        summary = "모델과 외부 도구를 이을 때마다 다른 방식으로 붙이면 재사용이 안 됩니다. 표준 프로토콜로 도구를 노출합니다.",
        code = """
            @Component
            class McpTools(private val orders: OrderService) {
                // @McpTool — MCP 프로토콜로 노출된다. 스키마는 시그니처에서 자동 생성
                @McpTool(name = "lookupOrder", description = "주문 번호로 주문 상태와 배송 정보를 조회한다")
                fun lookupOrder(
                    @McpToolParam(description = "주문 번호. 예: A-1024") orderId: String,
                ): String = orders.find(orderId)?.summary() ?: "주문번호 ${'$'}orderId 를 찾을 수 없습니다."
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
        val list = call("tools/list")
        ctx.note("tools/list — 선언한 함수가 스키마로 나옵니다. 개발자가 JSON 을 쓴 적은 없습니다")
        val invoke = call("tools/call", """{"name":"lookupOrder","arguments":{"orderId":"${ctx.input}"}}""")
        ctx.note("tools/call — 프로토콜로 실제 실행까지")
        return ctx.result("$list\n\n─────\n\n$invoke")
    }
}

@Component
class InterAgentCommunication : PatternRunner {
    override val spec = PatternSpec(
        15, "에이전트 간 통신", "Inter-Agent Communication",
        summary = "다른 프레임워크로 만든 에이전트끼리는 공통 언어가 없습니다. 규격을 정해 일을 위임하고 정보를 주고받습니다.",
        code = """
            // 서버 — 내 함수를 밖으로 연다
            implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

            // 클라이언트 — 남이 만든 MCP 서버를 내 에이전트의 도구로 가져온다
            implementation("org.springframework.ai:spring-ai-starter-mcp-client")
        """.trimIndent(),
        diagram = """
            flowchart LR
                P[파이썬 에이전트] -.tools/call.-> S[내 앱 · MCP 서버]
                S --> T[내 도구 실행]
                S2[내 앱 · MCP 클라이언트] -.tools/call.-> O[남의 MCP 서버]
                O --> T2[남의 도구 실행]
        """.trimIndent(),
        defaultInput = "A-1024",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        ctx.note("파이썬 에이전트가 이 서버를 부른다면 아래와 똑같은 JSON-RPC 를 보냅니다")
        val out = call("tools/call", """{"name":"lookupOrder","arguments":{"orderId":"${ctx.input}"}}""")
        ctx.note("프로토콜이 언어 중립이라 여기서 파이썬 LangGraph 에이전트와 Spring 서비스가 만납니다")
        ctx.note("모델 호출은 0회입니다 — 에이전트끼리의 통신이지 추론이 아닙니다")
        return ctx.result(out)
    }
}
