package com.example.demo.pattern.ch07

import com.example.demo.pattern.*
import org.springframework.stereotype.Component

@Component
class MultiAgent : PatternRunner {
    override val spec = PatternSpec(
        7, "멀티 에이전트", "Multi-Agent",
        summary = "하나의 에이전트가 모든 전문성을 갖출 수는 없습니다. 역할을 나눠 맡기고 결과를 합칩니다.",
        code = """
            // defaultSystem — 이 ChatClient 로 나가는 모든 호출에 붙는 고정 시스템 메시지
            @Bean fun 작성자(b: ChatClient.Builder) = b.defaultSystem("너는 초안을 쓴다").build()
            @Bean fun 검토자(b: ChatClient.Builder) = b.defaultSystem("너는 초안을 비판한다").build()

            // 누가 먼저 말하고 몇 번 주고받을지는 직접 짠다
            val draft  = 작성자.prompt().user(주제).call().content()
            val review = 검토자.prompt().user(draft).call().content()
        """.trimIndent(),
        diagram = """
            flowchart LR
                T[주제] --> M1((작성자))
                M1 --> D[초안]
                D --> M2((검토자))
                M2 --> R[고칠 점]
        """.trimIndent(),
        defaultInput = "환불 안내 문구",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        val draft = ctx.step("① 작성자") {
            it.system("너는 고객 안내 문구를 쓰는 작성자다. 두 문장으로 쓴다.").user(ctx.input).call().content()
        }
        val review = ctx.step("② 검토자") {
            it.system("너는 문구를 비판하는 검토자다. 고칠 점 한 가지만 짧게 말한다.").user(draft.orEmpty()).call().content()
        }
        ctx.note("빈은 둘, 순서와 종료는 코드가 정했습니다 — 조율 클래스는 없습니다")
        return ctx.result("초안 — $draft\n\n검토 — $review")
    }
}
