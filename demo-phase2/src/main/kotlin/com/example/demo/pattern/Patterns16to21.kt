package com.example.demo.pattern

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.AdvisorChain
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.core.ParameterizedTypeReference
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

@Component
class Guardrails : PatternRunner {
    override val spec = PatternSpec(
        18, "가드레일", "Guardrails",
        summary = "자율성이 커질수록 예측 못 한 출력이 나올 위험도 커집니다. 입력과 출력을 검사해 범위를 제한합니다.",
        code = """
            // BaseAdvisor — before / after / getOrder 셋만 구현하면 된다
            class MaskingAdvisor : BaseAdvisor {

                // 모델로 나가기 직전. 프롬프트를 고쳐서 돌려준다
                override fun before(req: ChatClientRequest, chain: AdvisorChain): ChatClientRequest =
                    req.mutate().prompt(마스킹(req.prompt())).build()

                // 모델에서 돌아온 직후. 응답을 검사하거나 바꿔치기한다
                override fun after(res: ChatClientResponse, chain: AdvisorChain): ChatClientResponse =
                    if (금칙어(res)) 거절응답() else res

                // 작을수록 먼저 실행된다. ToolCallingAdvisor 는 Integer.MIN_VALUE + 300
                override fun getOrder(): Int = 1000
            }
        """.trimIndent(),
        diagram = """
            flowchart LR
                Q[프롬프트] --> B[before<br/>카드번호 마스킹]
                B --> M((모델))
                M --> A[after<br/>금칙어 검사]
                A --> R[응답]
        """.trimIndent(),
        defaultInput = "내 카드번호는 1234-5678-9012-3456 인데 환불해줘",
        model = "granite4:micro",
    )

    override fun run(ctx: RunContext): RunResult {
        val guard = MaskingAdvisor()
        val answer = ctx.step("마스킹 후 호출") { it.user(ctx.input).advisors(guard).call().content() }
        ctx.note("원본 — ${guard.original}")
        ctx.note("모델에 나간 것 — ${guard.masked}")
        ctx.note("위 타임라인의 프롬프트를 펼쳐 보면 카드번호가 없습니다")
        return ctx.result(answer)
    }
}

/** 18장 가드레일 — 나가는 프롬프트를 가로채 마스킹한다. */
class MaskingAdvisor : BaseAdvisor {
    var original: String? = null
        private set
    var masked: String? = null
        private set

    override fun before(request: ChatClientRequest, chain: AdvisorChain): ChatClientRequest {
        val text = request.prompt().userMessage.text ?: return request
        original = text
        val hidden = CARD.replace(text, "****-****-****-****")
        masked = hidden
        if (hidden == text) return request
        return request.mutate()
            .prompt(request.prompt().augmentUserMessage { it.mutate().text(hidden).build() })
            .build()
    }

    override fun after(response: ChatClientResponse, chain: AdvisorChain) = response
    override fun getOrder(): Int = GUARDRAIL_ORDER

    private companion object {
        val CARD = Regex("""\d{4}-\d{4}-\d{4}-\d{4}""")
    }
}

@Component
class Evaluation : PatternRunner {
    override val spec = PatternSpec(
        19, "평가와 모니터링", "Evaluation and Monitoring",
        summary = "LLM은 확률적이라 기존 테스트로는 신뢰성을 보장할 수 없습니다. 답의 품질을 코드가 잽니다.",
        code = """
            // EvaluationRequest 는 세 가지를 받는다 — 질문, 근거로 삼을 문서, 채점할 답
            val response = evaluator.evaluate(EvaluationRequest(질문, 검색된문서, 답))

            response.isPass        // 통과 여부 (boolean)
            response.getScore()    // 점수 (float)
            response.getFeedback() // 왜 그렇게 판정했는지 (채점기에 따라 비어 있다)
        """.trimIndent(),
        diagram = """
            flowchart LR
                Q[질문] --> M1((모델<br/>답))
                M1 --> M2((모델<br/>채점))
                D[(근거 문서)] --> M2
                M2 --> R[통과 여부 · 점수 · 근거]
        """.trimIndent(),
        defaultInput = "단순 변심으로 환불하면 배송비 얼마 내야 해?",
        model = "granite4:micro",
    )

    override fun run(ctx: RunContext): RunResult {
        val good = ctx.step("① 답 만들기") {
            it.user("${ctx.input}\n사내 규정: 단순 변심이면 왕복 배송비 6,000원을 구매자가 부담합니다.").call().content()
        }
        listOf("정상 답" to good.orEmpty(), "엉뚱한 답" to "고객센터 영업시간은 평일 9시부터 6시까지입니다.")
            .forEach { (label, answer) ->
                val v = ctx.evaluate("② 채점 — $label", EvaluationRequest(ctx.input, Docs.POLICIES, answer))
                ctx.note(
                    if (v == null) "$label — 채점기 호출 실패"
                    else "$label — ${v.verdict()}",
                )
            }
        ctx.note("같은 채점기를 회귀 테스트에 그대로 씁니다")
        return ctx.result(good)
    }
}

@Component
class Prioritization : PatternRunner {
    override val spec = PatternSpec(
        20, "우선순위 결정", "Prioritization",
        summary = "할 수 있는 일은 많고 자원은 한정돼 있습니다. 무엇을 먼저 할지 정하지 못하면 비효율적으로 움직입니다.",
        code = """
            data class Task(val title: String, val priority: Int, val reason: String)

            val tasks = chatClient.prompt()
                .system("문의를 처리 우선순위로 정렬한다. priority 는 1~5, 클수록 급하다. reason 에는 그 순위인 근거를 한 문장으로.")
                .user(할일들)
                .call()
                // 컬렉션은 ParameterizedTypeReference 로 받는다
                .entity(object : ParameterizedTypeReference<List<Task>>() {})

            tasks.sortedByDescending { it.priority }.forEach { 처리(it) }
        """.trimIndent(),
        diagram = """
            flowchart LR
                T[할 일 목록] --> M((모델))
                M --> L[Task 목록<br/>priority 가 붙어 온다]
                L --> S[코드가 정렬]
                S --> P[순서대로 처리]
        """.trimIndent(),
        defaultInput = "배송 지연 항의, 신규 가입 문의, 결제 실패 신고, 단순 가격 문의",
        model = "qwen3:4b-instruct",
        maxTokens = 500,
    )

    override fun run(ctx: RunContext): RunResult {
        val tasks = ctx.step("우선순위 매기기") {
            it.system("고객 문의를 처리 우선순위로 정렬한다. priority 는 1~5, 클수록 급하다. reason 에는 제목을 반복하지 말고 그 순위인 근거를 한 문장으로 적는다.")
                .user(ctx.input).call().entity(object : ParameterizedTypeReference<List<Task>>() {})
        } ?: return ctx.result("목록을 타입으로 받지 못했습니다", ok = false)

        val sorted = tasks.sortedByDescending { it.priority }
        sorted.forEach { ctx.note("[${it.priority}] ${it.title} — ${it.reason.take(50)}") }
        ctx.note("정렬 기준은 모델이 정하고, 순서대로 도는 것은 코드가 합니다")
        return ctx.result(sorted.joinToString("\n") { "${it.priority} · ${it.title}" })
    }
}

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
                            .options(ChatOptions.builder().temperature(0.9))   // 온도를 높여 답을 벌린다
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
