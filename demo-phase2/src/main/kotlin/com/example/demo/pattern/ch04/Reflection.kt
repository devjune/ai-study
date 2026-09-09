package com.example.demo.pattern.ch04

import com.example.demo.pattern.*
import org.springframework.stereotype.Component

@Component
class Reflection : PatternRunner {
    override val spec = PatternSpec(
        4, "자기 검토", "Reflection",
        summary = "에이전트의 첫 출력이나 계획은 완벽하지 않을 수 있습니다. 스스로 평가하고 고쳐 쓰는 피드백 루프로 자기 개선을 합니다 — 여기서는 쓰는 쪽(Producer)과 검토하는 쪽(Critic)을 나눕니다.",
        code = """
            // Critic 이 채워 줄 그릇. reason 이 있어야 무엇을 고칠지가 남는다
            data class Critique(val pass: Boolean, val reason: String)

            val CRITIC = ${'"'}""
                너는 사내 규정을 검토하는 감사자다. 답을 새로 쓰지 말고 검토만 해라.
                pass   — 규정의 수치를 그대로 말하고 어긋남이 없을 때만 true
                reason — 규정의 어느 대목이 빠졌거나 틀렸는지 숫자까지 짚어 한 문장
            ${'"'}"".trimIndent()

            var answer = chatClient.prompt()                // Producer — 규정을 주지 않는다
                .system("고객 문의에 한 문장으로 답해라. 반드시 한국어 단어만 쓴다.")
                .user(질문).call().content()

            for (round in 1..2) {                           // 루프는 두 바퀴까지만 돈다
                val critique = chatClient.prompt()          // Critic — 별도 호출, 별도 프롬프트
                    .system(CRITIC)
                    .user("규정:\n${'$'}규정\n\n질문: ${'$'}질문\n답: ${'$'}answer")
                    .call()
                    .entity(Critique::class.java)           // 판정과 이유를 함께 받는다

                if (critique.pass) break

                answer = chatClient.prompt()                // Producer 가 지적을 받아 다시 쓴다
                    .user("${'$'}질문\n\n앞선 답: ${'$'}answer\n검토자 지적: ${'$'}{critique.reason}")
                    .call().content()
            }
        """.trimIndent(),
        diagram = """
            flowchart LR
                Q[질문] --> M1((모델<br/>초안))
                M1 --> M2((모델<br/>검토))
                M2 -->|통과| OK[끝]
                M2 -->|미달 + 이유| M3((모델<br/>고쳐 쓰기))
                M3 --> M2
        """.trimIndent(),
        defaultInput = "단순 변심으로 환불하면 배송비 얼마 내야 해?",
        model = "qwen3:4b-instruct",
    )

    /** Critic 의 페르소나. Producer 와 다른 지시를 받는 것이 이 패턴의 핵심이다. */
    private val critic = """
        너는 사내 규정을 검토하는 감사자다. 답을 새로 쓰지 말고 검토만 해라.
        pass 는 아래 둘을 모두 만족할 때만 true 다.
          1. 규정에 있는 구체적인 수치·기한을 답이 그대로 말하고 있다
          2. 규정과 어긋나는 내용이 없다
        일반론으로 얼버무리거나 규정의 수치를 빠뜨렸으면 false 다.
        reason 은 한 문장. 규정의 어느 대목이 어떻게 빠졌거나 틀렸는지 숫자까지 짚어라.
    """.trimIndent()

    override fun run(ctx: RunContext): RunResult {
        val rules = Docs.POLICIES.joinToString("\n") { "- ${it.text}" }

        // Producer — 규정을 주지 않는다. 첫 답이 어디까지 지어내는지가 이 장의 출발점이다
        var answer = ctx.step("① 초안 (Producer)") {
            it.system("고객 문의에 한 문장으로 답해라. 반드시 한국어 단어만 쓴다.").user(ctx.input).call().content()
        }

        for (round in 1..2) {
            val critique = runCatching {
                ctx.step("② 검토 (Critic)") {
                    it.system(critic)
                        .user("규정:\n$rules\n\n질문: ${ctx.input}\n답: $answer")
                        .call().entity(Critique::class.java)
                }
            }.getOrNull()
            if (critique == null) { ctx.note("검토가 형식을 못 맞췄습니다"); break }

            ctx.note("${round}차 검토 — ${if (critique.pass) "통과" else "미달"} · ${critique.reason}")
            if (critique.pass) break

            answer = ctx.step("③ 고쳐 쓰기 (Producer)") {
                // 지적만 넘긴다. 규정 원문은 주지 않는다 — 고쳐 쓰는 근거가 검토라는 것이 이 장의 요점이다
                it.system("고객 문의에 한 문장으로 답해라. 반드시 한국어 단어만 쓴다.")
                    .user("${ctx.input}\n\n앞선 답: $answer\n검토자 지적: ${critique.reason}\n지적을 반영해 다시 답해라.")
                    .call().content()
            }
        }
        ctx.note("초안을 쓴 쪽과 검토한 쪽은 프롬프트가 다릅니다. 자기 답을 자기가 보면 판정이 후해집니다")
        return ctx.result(answer)
    }
}

/** Critic 이 채워 주는 검토 결과. reason 이 있어야 고쳐 쓸 근거가 생긴다. */
data class Critique(val pass: Boolean, val reason: String)
