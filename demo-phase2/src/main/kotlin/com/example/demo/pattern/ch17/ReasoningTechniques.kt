package com.example.demo.pattern.ch17

import com.example.demo.pattern.*
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.stereotype.Component

@Component
class ReasoningTechniques : PatternRunner {
    override val spec = PatternSpec(
        17, "추론 기법", "Reasoning Techniques",
        summary = "같은 모델, 같은 질문에 시스템 프롬프트 한 줄만 다릅니다. 결론만 시킨 답과 단계를 밟게 한 답이 갈리고, 그 대가는 토큰과 시간입니다.",
        code = """
            // ① 결론만 시키기 — 요즘 모델은 시키지 않아도 단계를 밟는다. 대비를 보려면 이쪽을 눌러야 한다
            chatClient.prompt()
                .system("계산 과정을 쓰지 말고 결론만 한 줄로 답해라")
                .user(질문)
                .call().content()

            // ② 단계를 밟게 시키기 — zero-shot CoT. 프레임워크가 개입할 자리는 없다. 차이는 이 한 줄이다
            chatClient.prompt()
                .system("답하기 전에 근거를 단계별로 적고, 마지막 줄에 결론만 남겨라")
                .user(질문)
                .call().content()

            // 다른 층 — 프롬프트가 아니라 모델이 스스로 단계를 밟는 쪽(thinking). qwen3:8b 처럼 thinking 이 있는
            // 모델에서만 뜻이 있다. 이 장의 기본 모델(qwen3:4b-instruct)은 non-thinking 이라 켜도 달라지지 않는다
            OllamaChatOptions.builder().enableThinking()   // thinkLow · thinkMedium · thinkHigh 로 깊이도 정한다
        """.trimIndent(),
        diagram = """
            flowchart LR
                Q[질문] --> M1((모델))
                M1 --> A1[바로 답]
                Q --> P[단계를 밟으라는<br/>시스템 프롬프트]
                P --> M2((모델))
                M2 --> A2[근거 → 결론]
        """.trimIndent(),
        defaultInput = "아버지는 아들보다 24살 많다. 2년 뒤에는 아버지 나이가 아들 나이의 3배가 된다. 아들은 지금 몇 살인가?",
        model = "qwen3:4b-instruct",
        maxTokens = 700,
    )

    override fun run(ctx: RunContext): RunResult {
        val t0 = System.nanoTime()
        val direct = ctx.step("① 결론만 시키기") {
            it.system("계산 과정을 쓰지 말고 결론만 한 줄로 답해라").user(ctx.input).call().content()
        }
        val t1 = System.nanoTime()
        val guided = ctx.step("② 단계를 밟게 시키기") {
            it.system("답하기 전에 근거를 단계별로 적고, 마지막 줄에 결론만 남겨라").user(ctx.input).call().content()
        }
        val t2 = System.nanoTime()
        val directMs = (t1 - t0) / 1_000_000
        val guidedMs = (t2 - t1) / 1_000_000
        ctx.note("요즘 모델은 시키지 않아도 단계를 밟습니다. 그래서 ① 을 오히려 결론만 쓰게 눌러야 대비가 보입니다")
        ctx.note("프레임워크가 개입할 자리가 없습니다. 차이는 시스템 프롬프트 한 줄입니다")
        ctx.note("② 가 ① 보다 ${if (directMs > 0) guidedMs / directMs else 0}배 오래 걸렸습니다 (${directMs}ms → ${guidedMs}ms) — 단계는 토큰이고 토큰은 시간입니다")
        ctx.note("적힌 단계는 생각의 기록이 아니라 생성된 글입니다. 중간 줄이 틀려도 결론이 맞을 수 있고, 반대도 가능합니다")
        return ctx.result("직답 — $direct\n\n단계 유도 — $guided")
    }
}
