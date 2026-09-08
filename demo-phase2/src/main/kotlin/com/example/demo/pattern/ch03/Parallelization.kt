package com.example.demo.pattern.ch03

import com.example.demo.pattern.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component

@Component
class Parallelization : PatternRunner {
    override val spec = PatternSpec(
        3, "병렬화", "Parallelization",
        summary = "서로 기다릴 이유가 없는 작업까지 순서대로 돌리면 느립니다. 독립적인 호출을 동시에 던집니다.",
        code = """
            val (요약, 감정) = coroutineScope {
                val a = async { chatClient.prompt().user("이 글을 요약해줘: ${'$'}글").call().content() }
                val b = async { chatClient.prompt().user("이 글의 감정을 분류해줘: ${'$'}글").call().content() }
                a.await() to b.await()
            }
        """.trimIndent(),
        diagram = """
            flowchart LR
                G[글] --> M1((모델<br/>요약))
                G --> M2((모델<br/>감정))
                M1 --> R[합침]
                M2 --> R
        """.trimIndent(),
        defaultInput = "배송이 사흘이나 늦었는데 안내 문자도 없었습니다. 그래도 제품은 마음에 듭니다.",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult = runBlocking {
        val started = System.nanoTime()
        val (summary, sentiment) = coroutineScope {
            val a = async { ctx.step("① 요약") { it.user("한 문장으로 요약해줘: ${ctx.input}").call().content() } }
            val b = async { ctx.step("② 감정 분류") { it.user("긍정/부정/중립 중 하나로만 답해라: ${ctx.input}").call().content() } }
            listOf(a, b).awaitAll()
        }
        val wall = (System.nanoTime() - started) / 1_000_000
        val serial = ctx.calls.sumOf { it.elapsedMs }
        ctx.note("순차로 돌렸다면 ${serial}ms, 동시에 던져 ${wall}ms")
        ctx.result("요약 — $summary\n\n감정 — $sentiment", elapsedMs = wall)
    }
}
