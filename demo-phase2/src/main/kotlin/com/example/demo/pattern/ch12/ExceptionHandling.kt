package com.example.demo.pattern.ch12

import com.example.demo.pattern.*
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.stereotype.Component

@Component
class ExceptionHandling : PatternRunner {
    override val spec = PatternSpec(
        12, "예외 처리와 복구", "Exception Handling and Recovery",
        summary = "실제 환경에서는 도구가 실패하고 네트워크가 끊깁니다. 감지해서 복구 경로로 넘깁니다.",
        code = """
            # 기본값은 10회에 2초 → 50초 백오프. 모델이 없으면 화면이 몇 분 멈춘다
            spring.ai.retry.max-attempts=1

            val answer = runCatching {
                chatClient.prompt().user(질문).call().content()
            }.getOrElse { e ->
                // 폴백은 장애에만 건다. 설정 오류나 버그까지 이 문구로 덮으면 고장이 안 보인다
                if (e !is TransientAiException) throw e
                log.warn("model unavailable, falling back", e)
                "지금 답변이 어렵습니다. 상담원에게 연결해 드릴까요?"
            }
        """.trimIndent(),
        diagram = """
            flowchart LR
                Q[요청] --> M1((모델))
                M1 -->|성공| A[답]
                M1 -->|실패| C[runCatching]
                C --> F[폴백 문구]
        """.trimIndent(),
        defaultInput = "환불 규정 알려줘",
        model = "granite4:micro",
    )

    override fun run(ctx: RunContext): RunResult {
        // 없는 모델을 불러 실제로 실패시킨다
        val failed = runCatching {
            ctx.chatClient.prompt().user(ctx.input)
                .options(ChatOptions.builder().model("존재하지-않는-모델")).call().content()
        }
        ctx.note(
            failed.exceptionOrNull()
                ?.let { "1차 시도 실패 — ${it::class.simpleName}: ${it.message?.take(90)}" }
                ?: "실패하지 않았습니다",
        )
        ctx.note("NonTransient — 다시 보내도 똑같이 실패하는 부류입니다. 위 코드라면 폴백에 안 걸리고 그대로 터집니다")
        ctx.note("재시도를 1회로 낮춰 두어 2초 안에 실패가 돌아옵니다. 기본값 10회면 몇 분을 기다립니다")

        val answer = ctx.step("고친 설정으로 복구") { it.system("고객 상담원이다. 문의에 한국어 두 문장 안으로 답한다.").user(ctx.input).call().content() }
        return ctx.result(answer)
    }
}
