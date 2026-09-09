package com.example.demo.pattern.ch21

import com.example.demo.pattern.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component

/** 심사위원 한 명의 한 표. 점수 대신 둘 중 하나를 고른다 */
data class Vote(val label: String, val winner: Int, val weakness: String)

@Component
class Exploration : PatternRunner {
    override val spec = PatternSpec(
        21, "탐색과 발견", "Exploration and Discovery",
        summary = "정답의 범위가 정해지지 않은 문제에서는 넣어 둔 지식만으로 부족합니다. 후보를 여럿 만들고, 다른 눈으로 겨루게 하고, 이긴 것을 키웁니다.",
        code = """
            // 심사위원 한 명의 한 표. 점수를 매기는 대신 둘 중 하나를 고른다
            data class Vote(val label: String, val winner: Int, val weakness: String)

            // ① 만들고 — 온도를 올려 서로 다른 후보를 동시에 뽑는다 (3장)
            val 후보 = (1..2).map { async { chatClient.prompt().user(질문)
                .options(ChatOptions.builder().temperature(0.9)).call().content() } }.awaitAll()

            // ② 겨루고 — 둘을 나란히 놓고 세 관점이 각각 한 표씩 던진다 (20장처럼 목록을 타입으로)
            val 표 = chatClient.prompt().system(JUDGE)
                .user("후보1: ${'$'}{후보[0]} · 후보2: ${'$'}{후보[1]}")
                .call().entity(object : ParameterizedTypeReference<List<Vote>>() {})

            val 이긴답 = if (표.count { it.winner == 2 } > 1) 후보[1] else 후보[0]   // 다수결

            // ③ 키운다 — 이긴 답에 진 쪽 약점을 붙여 한 바퀴 더 (4장)
            val 다듬은답 = chatClient.prompt()
                .user(질문 + 이긴답 + 표.joinToString { it.weakness }).call().content()
        """.trimIndent(),
        diagram = """
            flowchart LR
                Q[질문] --> C1((후보 1))
                Q --> C2((후보 2))
                C1 --> J((겨루기<br/>세 관점이 한 표씩))
                C2 --> J
                J --> W[이긴 답 + 진 쪽 약점]
                W --> E((다듬기))
                E --> R[결과]
        """.trimIndent(),
        defaultInput = "가을 세일 안내 문구를 한 문장으로 써줘",
        model = "qwen3:4b-instruct",
        maxTokens = 150,
    )

    /** 세 관점을 한 호출로. 점수를 매기지 않고 둘 중 하나를 고르게 한다 — 원서 Ranking 의 토너먼트 */
    private val judge = """
        심사위원 셋이 후보 1 과 후보 2 를 비교한다. 반드시 항목 세 개를 낸다 — 심사위원마다 하나씩이다.
        1) label "엄격"    — 근거가 분명한 쪽을 고른다
        2) label "파급력"  — 고객에게 실제로 도움이 되는 쪽을 고른다
        3) label "새로움"  — 덜 뻔한 쪽을 고른다
        winner 에는 고른 후보 번호(1 또는 2)를, weakness 에는 고르지 않은 쪽의 약점을 20자 이내로 적는다.
    """.trimIndent()

    override fun run(ctx: RunContext): RunResult = runBlocking {
        // ① 만들고 — 온도를 올려 서로 다른 후보를
        val candidates = coroutineScope {
            (1..2).map { n ->
                async {
                    ctx.step("① 후보 $n") {
                        it.user(ctx.input)
                            .options(ChatOptions.builder().model(ctx.model).temperature(0.9).maxTokens(90))
                            .call().content()
                    }.orEmpty()
                }
            }.awaitAll()
        }

        // ② 겨루고 — 둘을 나란히 놓고 세 관점이 한 표씩
        val votes = runCatching {
            ctx.step("② 겨루기") {
                it.system(judge)
                    .options(ChatOptions.builder().model(ctx.model).maxTokens(150))
                    .user("후보1: ${candidates[0]}\n후보2: ${candidates[1]}")
                    .call().entity(object : ParameterizedTypeReference<List<Vote>>() {})
            }
        }.getOrNull().orEmpty().ifEmpty { listOf(Vote("?", 1, "판정 형식을 못 맞춤")) }

        val winner = if (votes.count { it.winner == 2 } > votes.size / 2) candidates[1] else candidates[0]
        candidates.forEachIndexed { i, c -> ctx.note("후보 ${i + 1} · ${c.take(60)}${if (c.length > 60) "…" else ""}") }
        votes.forEach { ctx.note("${it.label} — 후보 ${it.winner} 승 · 진 쪽 약점: ${it.weakness.take(40)}") }

        // ③ 키운다 — 이긴 답에 약점을 붙여 한 바퀴 더
        val refined = ctx.step("③ 다듬기") {
            it.options(ChatOptions.builder().model(ctx.model).maxTokens(90))
                .user("${ctx.input}\n\n앞선 답: $winner\n지적: ${votes.joinToString { v -> v.weakness }}\n지적을 반영해 한국어 한 문장으로 다시 써라.")
                .call().content()
        }
        ctx.note("호출 네 번 — 후보 2 + 겨루기 1 + 다듬기 1. 후보마다 심사위원 셋을 따로 불렀다면 아홉 번입니다")
        ctx.result(refined)
    }
}
