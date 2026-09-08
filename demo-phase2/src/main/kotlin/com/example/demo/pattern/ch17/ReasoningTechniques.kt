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

            OllamaChatOptions.builder().disableThinking()   // 공통 ChatOptions 에는 없다
        """.trimIndent(),
        diagram = """
            flowchart LR
                Q[질문] --> M1((모델))
                M1 --> A1[바로 답]
                Q --> P[단계를 밟으라는<br/>시스템 프롬프트]
                P --> M2((모델))
                M2 --> A2[근거 → 결론]
        """.trimIndent(),
        defaultInput = "사과 3개를 5,000원에 사서 2개를 3,000원에 팔았다. 남은 1개를 얼마에 팔아야 본전인가?",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        val plain = ctx.step("① 그냥 묻기") { it.user(ctx.input).call().content() }
        val guided = ctx.step("② 단계를 밟게 시키기") {
            it.system("답하기 전에 근거를 단계별로 적고, 마지막 줄에 결론만 남겨라").user(ctx.input).call().content()
        }
        ctx.note("프레임워크가 개입할 자리가 없습니다. 차이는 시스템 프롬프트 한 줄입니다")
        return ctx.result("그냥 — $plain\n\n단계 유도 — $guided")
    }
}
