package com.example.demo.pattern.ch17

import com.example.demo.pattern.*
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.stereotype.Component

@Component
class ReasoningTechniques : PatternRunner {
    override val spec = PatternSpec(
        17, "추론 기법", "Reasoning Techniques",
        summary = "한 번에 답이 나오지 않는 문제가 있습니다. 모델이 단계를 밟아 생각하도록 프롬프트로 유도합니다.",
        code = """
            chatClient.prompt()
                .system("답하기 전에 근거를 단계별로 적고, 마지막 줄에 결론만 남겨라")
                .user(질문)
                .call().content()

            OllamaChatOptions.builder().disableThinking()   // 끈다
            OllamaChatOptions.builder().enableThinking()    // 켠다. thinkLow · thinkMedium · thinkHigh 로 깊이도 정한다
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
        val direct = ctx.step("① 결론만 시키기") {
            it.system("계산 과정을 쓰지 말고 결론만 한 줄로 답해라").user(ctx.input).call().content()
        }
        val guided = ctx.step("② 단계를 밟게 시키기") {
            it.system("답하기 전에 근거를 단계별로 적고, 마지막 줄에 결론만 남겨라").user(ctx.input).call().content()
        }
        ctx.note("요즘 모델은 시키지 않아도 단계를 밟습니다. 그래서 ① 을 오히려 결론만 쓰게 눌러야 대비가 보입니다")
        ctx.note("프레임워크가 개입할 자리가 없습니다. 차이는 시스템 프롬프트 한 줄입니다")
        return ctx.result("직답 — $direct\n\n단계 유도 — $guided")
    }
}
