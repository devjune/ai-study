package com.example.demo.pattern.ch16

import com.example.demo.pattern.*
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.stereotype.Component

@Component
class ResourceAware : PatternRunner {
    override val spec = PatternSpec(
        16, "모델 골라 쓰기", "Resource-Aware Optimization",
        summary = "LLM 호출은 비싸고 느립니다. 모든 일에 가장 좋은 모델을 쓸 이유가 없습니다.",
        code = """
            // 분류·요약처럼 정해진 형식만 뽑는 일과, 근거를 들어 답해야 하는 일을 가른다
            val model = when (decision.route()) {
                Route.ORDER   -> "qwen3:1.7b"          // 도구가 조회한 값을 옮겨 적기만 한다
                Route.GENERAL -> "qwen3:1.7b"          // 인사·잡담
                Route.POLICY  -> "qwen3:4b-instruct"   // 규정 문서를 읽고 근거를 들어야 한다
            }

            chatClient.prompt().user(질문)
                // ChatOptions — 벤더 중립 옵션. 재시작 없이 모델만 교체된다
                .options(ChatOptions.builder().model(model))
                .call().content()
        """.trimIndent(),
        diagram = """
            flowchart LR
                W[들어온 일] --> D{난이도}
                D -->|쉬움| S((작은 모델))
                D -->|어려움| B((큰 모델))
                S --> M[토큰 지표]
                B --> M
        """.trimIndent(),
        defaultInput = "이 문장이 긍정인지 부정인지 한 단어로만 답해라: 배송이 너무 늦었어요",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        val small = timed(ctx, "① 작은 모델 (qwen3:1.7b)", "qwen3:1.7b")
        val big = timed(ctx, "② 기본 모델 (${ctx.model})", ctx.model)
        ctx.note("메모리에 없던 모델은 첫 호출에 적재 시간이 섞입니다. 한 번 더 눌러 보면 제 속도가 나옵니다")
        ctx.note("같은 일에 큰 모델을 쓸 이유가 없다면 작은 쪽이 이득입니다")
        return ctx.result("작은 모델 — ${small.first} (${small.second}ms)\n\n기본 모델 — ${big.first} (${big.second}ms)")
    }

    private fun timed(ctx: RunContext, label: String, model: String): Pair<String?, Long> {
        val t0 = System.nanoTime()
        val out = runCatching {
            ctx.step(label) { it.user(ctx.input).options(ChatOptions.builder().model(model)).call().content() }
        }.getOrElse { "(모델 없음: $model)" }
        val ms = (System.nanoTime() - t0) / 1_000_000
        ctx.note("$label — ${ms}ms")
        return out to ms
    }
}
