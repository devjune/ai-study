package com.example.demo.pattern.ch15

import com.example.demo.pattern.*
import org.springframework.stereotype.Component

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
        val out = mcpCall("tools/call", """{"name":"lookupOrder","arguments":{"orderId":"${ctx.input}"}}""")
        ctx.note("프로토콜이 언어 중립이라 여기서 파이썬 LangGraph 에이전트와 Spring 서비스가 만납니다")
        ctx.note("모델 호출은 0회입니다 — 에이전트끼리의 통신이지 추론이 아닙니다")
        return ctx.result(out)
    }
}
