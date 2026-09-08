package com.example.demo.pattern.ch21

import com.example.demo.pattern.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.stereotype.Component

@Component
class Exploration : PatternRunner {
    override val spec = PatternSpec(
        21, "탐색과 발견", "Exploration and Discovery",
        summary = "정답의 범위가 정해지지 않은 문제에서는 넣어 둔 지식만으로 부족합니다. 여러 갈래를 시도해 나은 것을 찾습니다.",
        code = """
            val 후보들 = coroutineScope {
                (1..3).map {
                    async {
                        chatClient.prompt().user(질문)
                            .options(ChatOptions.builder().temperature(0.9))   // 온도를 높인다. 낮으면 세 번 다 거의 같은 답이 온다
                            .call().content()
                    }
                }.awaitAll()
            }

            val 최선 = 후보들.maxByOrNull {
                evaluator.evaluate(EvaluationRequest(질문, it)).score      // 점수로 고른다
            }
        """.trimIndent(),
        diagram = """
            flowchart LR
                Q[질문] --> M1((후보 1))
                Q --> M2((후보 2))
                Q --> M3((후보 3))
                M1 --> E((채점 ×3))
                M2 --> E
                M3 --> E
                E --> B[점수가 가장 높은 답]
        """.trimIndent(),
        defaultInput = "환불 안내 문구를 한 문장으로 써줘",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult = runBlocking {
        val candidates = coroutineScope {
            (1..3).map { n ->
                async {
                    ctx.step("후보 $n 생성") {
                        it.user(ctx.input).options(ChatOptions.builder().model(ctx.model).temperature(0.9)).call().content()
                    }
                }
            }.awaitAll()
        }
        val scored = candidates.filterNotNull().mapIndexed { i, c ->
            val score = ctx.evaluate("후보 ${i + 1} 채점", EvaluationRequest(ctx.input, c))?.score ?: 0f
            ctx.note("%.2f · %s".format(score, c.take(60)))
            c to score
        }
        val best = scored.maxByOrNull { it.second }?.first
        ctx.note("호출이 후보 수만큼 늘어납니다. 비용을 먼저 계산해야 합니다")
        ctx.result(best)
    }
}
