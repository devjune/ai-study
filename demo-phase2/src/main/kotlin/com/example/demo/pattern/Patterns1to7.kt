package com.example.demo.pattern

import com.example.demo.support.OrderService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.stereotype.Component

@Component
class PromptChaining : PatternRunner {
    override val spec = PatternSpec(
        1, "프롬프트 체이닝", "Prompt Chaining",
        summary = "복잡한 작업을 프롬프트 하나에 몰아넣으면 모델이 지시를 빠뜨립니다. 단계로 쪼개 앞 결과를 뒤 프롬프트에 넣습니다.",
        code = """
            // 모델이 채워 줄 그릇. 이 클래스로 JSON 스키마가 만들어져 프롬프트에 함께 실리고,
            // 돌아온 JSON 은 Jackson 이 생성자 파라미터 이름을 맞춰 그대로 채운다
            data class Outline(val sections: List<String>)

            val outline = chatClient.prompt()
                .user("${'$'}주제 로 목차를 잡아줘")
                .call()
                .entity(Outline::class.java)      // 답을 문자열이 아니라 객체로 받는다

            val draft = chatClient.prompt()
                .user("아래 목차로 초안을 써줘\n" + outline.sections.joinToString("\n"))
                .call()
                .content()
        """.trimIndent(),
        diagram = """
            flowchart LR
                A[주제] --> M1((모델))
                M1 --> O[Outline 객체]
                O --> M2((모델))
                M2 --> R[초안]
        """.trimIndent(),
        defaultInput = "사내 개발자 대상 Spring AI 스터디",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        val outline = ctx.step("① 목차 잡기") {
            it.user("${ctx.input} 로 발표 목차를 3개 항목으로 잡아줘").call().entity(Outline::class.java)
        } ?: return ctx.result("목차를 타입으로 받지 못했습니다", ok = false)

        ctx.note("중간 결과가 타입이라 파싱이 없다 — sections ${outline.sections.size}개")

        val draft = ctx.step("② 초안 쓰기") {
            it.user("아래 목차로 두 문장짜리 소개를 써줘\n" + outline.sections.joinToString("\n")).call().content()
        }
        return ctx.result(draft)
    }
}

@Component
class Routing(private val orders: OrderService, private val docs: Docs) : PatternRunner {
    override val spec = PatternSpec(
        2, "라우팅", "Routing",
        summary = "입력의 의도를 먼저 판별하고 거기에 맞는 경로로만 보냅니다. 주문 조회에 사내 문서를 검색할 이유가 없습니다.",
        code = """
            data class RouteDecision(val route: String, val reason: String) {
                // route 값은 모델이 쓴 텍스트다. "Order" 로 오든 엉뚱한 값이 오든 여기서 좁힌다
                fun route(): Route =
                    Route.entries.firstOrNull { it.name.equals(route.trim(), true) } ?: Route.GENERAL
            }

            // 분류 규칙의 전부. 경로를 늘리거나 기준을 바꾸려면 여기를 고친다
            val CLASSIFIER = ${'"'}""
                너는 고객 문의를 분류하는 라우터다. 질문에 답하지 말고 분류만 해라.
                - order   : 주문번호·배송 상태 등 주문 조회 시스템을 봐야 하는 질문
                - policy  : 환불·교환·장비 대여 등 사내 규정 문서를 찾아야 하는 질문
                - general : 위 둘에 해당하지 않는 일반 질문
                route 에는 order, policy, general 중 하나만 적는다.
            ${'"'}"".trimIndent()

            val decision = chatClient.prompt()
                .system(CLASSIFIER)                     // 분류 규칙을 시스템 메시지로
                .user(질문)
                .call()
                .entity(RouteDecision::class.java)

            when (decision.route()) {                   // 여기부터는 평범한 코틀린
                Route.ORDER   -> request.tools(SupportTools(orders)).call().content()
                Route.POLICY  -> request.advisors(qaAdvisor).call().content()
                Route.GENERAL -> request.call().content()
            }
        """.trimIndent(),
        diagram = """
            flowchart LR
                Q[질문] --> M1((모델<br/>분류))
                M1 --> R{route}
                R -->|order| T[도구]
                R -->|policy| V[(규정 문서)]
                R -->|general| G[아무것도 안 붙임]
                T --> M2((모델))
                V --> M2
                G --> M2
        """.trimIndent(),
        defaultInput = "A-1024 지금 어디쯤 왔어?",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        val decision = ctx.step("① 분류") {
            it.system(CLASSIFIER).user(ctx.input).call().entity(RouteDecision::class.java)
        } ?: RouteDecision("general", "분류 실패 — 가장 안전한 경로로")

        ctx.note("라우터 판단: ${decision.route} — ${decision.reason.take(60)}")

        val answer = when (decision.route()) {
            Route.ORDER -> {
                val tools = SupportTools(orders)
                val reply = ctx.step("② 주문 조회 경로") { it.user(ctx.input).tools(tools).call().content() }
                ctx.note(tools.callSummary())
                reply
            }
            Route.POLICY -> ctx.step("② 문서 검색 경로") {
                it.user(ctx.input).advisors(QuestionAnswerAdvisor.builder(docs.store).build()).call().content()
            }
            Route.GENERAL -> ctx.step("② 일반 경로") { it.user(ctx.input).call().content() }
        }
        return ctx.result(answer)
    }

    private companion object {
        val CLASSIFIER = """
            너는 고객 문의를 분류하는 라우터다. 질문에 답하지 말고 분류만 해라.
            - order   : 주문번호·배송 상태 등 주문 조회 시스템을 봐야 하는 질문
            - policy  : 환불·교환·장비 대여 등 사내 규정 문서를 찾아야 하는 질문
            - general : 위 둘에 해당하지 않는 일반 질문
            route 에는 order, policy, general 중 하나만 적는다.
        """.trimIndent()
    }
}

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

@Component
class Reflection : PatternRunner {
    override val spec = PatternSpec(
        4, "리플렉션", "Reflection",
        summary = "모델의 첫 답은 대개 부족합니다. 답을 낸 뒤 그 답을 다시 채점해 미달이면 고쳐 씁니다.",
        code = """
            // RelevancyEvaluator — 답이 질문·문맥에 맞는지 모델에게 되묻는 채점기
            val evaluator = RelevancyEvaluator.builder()
                .chatClientBuilder(builder)      // 채점에도 모델을 쓴다. 작은 모델이면 충분
                .build()

            var answer = chatClient.prompt().user(질문).call().content()

            for (round in 1..2) {                // 최대 두 번까지만 고쳐 쓴다
                // 문서들 — 채점의 근거가 될 문맥. 이것에 비추어 답이 맞는지를 판정한다
                val verdict = evaluator.evaluate(EvaluationRequest(질문, 문서들, answer))
                if (verdict.isPass) break                    // isPass — 통과하면 더 부르지 않는다
                answer = chatClient.prompt()
                    .user("${'$'}질문\n\n앞선 답은 이런 점이 부족했다: ${'$'}{verdict.feedback}")
                    .call().content()
            }
        """.trimIndent(),
        diagram = """
            flowchart LR
                Q[질문] --> M1((모델<br/>첫 답))
                M1 --> M2((모델<br/>채점))
                M2 -->|통과| OK[끝]
                M2 -->|미달| M3((모델<br/>고쳐 쓰기))
                M3 --> M2
        """.trimIndent(),
        defaultInput = "환불 정책을 한 문장으로 알려줘",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        var answer = ctx.step("① 첫 답") { it.user(ctx.input).call().content() }

        for (round in 1..2) {
            val verdict = ctx.evaluate("② 채점 (Evaluator)", EvaluationRequest(ctx.input, Docs.POLICIES, answer.orEmpty()))
            if (verdict == null) { ctx.note("채점기 호출 실패 — 모델이 형식을 못 맞췄습니다"); break }
            ctx.note("${round}차 채점 — ${verdict.verdict()}")
            if (verdict.isPass) break                     // 통과하면 더 부르지 않는다
            answer = ctx.step("③ 고쳐 쓰기") {
                it.user("${ctx.input}\n\n앞선 답은 근거가 부족했다. 사내 규정에 근거해 다시 답해라.\n앞선 답: $answer").call().content()
            }
        }
        return ctx.result(answer)
    }
}

@Component
class ToolUse(private val orders: OrderService) : PatternRunner {
    override val spec = PatternSpec(
        5, "도구 사용", "Tool Use",
        summary = "모델의 지식은 학습 시점에 멈춰 있습니다. 도구 사용은 모델이 필요할 때 외부 함수를 부르게 합니다.",
        code = """
            class SupportTools(private val orders: OrderService) {

                // @Tool      — 이 함수를 모델이 부를 수 있는 도구로 등록한다
                // description — 모델이 읽는 유일한 설명. 언제 부를지 여기서 판단한다
                @Tool(description = "주문 번호로 주문 상태와 배송 정보를 조회한다")
                fun lookupOrder(
                    // @ToolParam — 인자 설명. 스키마의 description 으로 들어간다
                    @ToolParam(description = "주문 번호. 예: A-1024") orderId: String,
                ): String = orders.find(orderId)?.summary() ?: "주문번호 ${'$'}orderId 를 찾을 수 없습니다."
            }

            chatClient.prompt()                   // 호출 하나를 조립하기 시작한다
                .user(질문)
                .tools(SupportTools(orders))      // @Tool 이 붙은 객체를 통째로 넘긴다
                .call()                           // 동기 호출 (토큰을 흘리려면 .stream())
                .content()                        // 응답을 문자열로. 타입으로 받으려면 .entity()
        """.trimIndent(),
        diagram = """
            flowchart LR
                Q[질문] --> M1((모델))
                M1 -->|이 함수를 불러 달라| F["@Tool 실행"]
                F -->|결과| M2((모델))
                M2 --> A[최종 답]
        """.trimIndent(),
        defaultInput = "A-1024 지금 어디쯤 왔어?",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        val tools = SupportTools(orders)
        val answer = ctx.step("도구 왕복") { it.user(ctx.input).tools(tools).call().content() }
        ctx.note(tools.callSummary())
        if (tools.calls.isNotEmpty())
            ctx.note("`.call()` 한 번인데 위 타임라인은 두 칸입니다. 프레임워크가 왕복을 돌았기 때문입니다")
        return ctx.result(answer)
    }
}

@Component
class Planning : PatternRunner {
    override val spec = PatternSpec(
        6, "계획 수립", "Planning",
        summary = "복잡한 요청은 한 번의 행동으로 끝나지 않습니다. 목표를 단계로 나누고 순서대로 실행합니다.",
        code = """
            data class Plan(val steps: List<Step>)
            data class Step(val order: Int, val instruction: String)

            val plan = chatClient.prompt()
                .system("목표를 실행 가능한 단계로 쪼개라. instruction 은 한 문장씩.")
                .user("신입 개발자 첫 주 온보딩")
                .call()
                .entity(Plan::class.java)

            // plan 에는 이런 것이 담겨 온다. 단계 수도 내용도 실행할 때마다 모델이 정한다
            // Plan(steps = [
            //     Step(order = 1, instruction = "개발 환경과 계정을 준비한다"),
            //     Step(order = 2, instruction = "첫 주에 만날 팀과 일정을 표로 정리한다"),
            // ])

            for (step in plan.steps.sortedBy { it.order }) {   // 실행은 여기서 직접
                chatClient.prompt().user(step.instruction).call().content()
            }
        """.trimIndent(),
        diagram = """
            flowchart LR
                G[목표] --> M1((모델<br/>계획))
                M1 --> P[Plan.steps]
                P --> L[for 문]
                L --> M2((모델<br/>단계 실행))
                M2 --> L
        """.trimIndent(),
        defaultInput = "신입 개발자 첫 주 온보딩",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        val plan = ctx.step("① 계획 세우기") {
            it.system("목표를 실행 가능한 단계 2개로만 쪼개라. instruction 은 한 문장.")
                .user(ctx.input).call().entity(Plan::class.java)
        } ?: return ctx.result("계획을 타입으로 받지 못했습니다", ok = false)

        ctx.note("계획 ${plan.steps.size}단계 — " + plan.steps.joinToString(" / ") { it.instruction.take(30) })

        val done = plan.steps.sortedBy { it.order }.take(2).mapIndexed { i, step ->
            ctx.step("② 단계 ${i + 1} 실행") { it.user("한 문장으로 답해라: ${step.instruction}").call().content() }
        }
        return ctx.result(done.joinToString("\n\n"))
    }
}

@Component
class MultiAgent : PatternRunner {
    override val spec = PatternSpec(
        7, "멀티 에이전트", "Multi-Agent",
        summary = "하나의 에이전트가 모든 전문성을 갖출 수는 없습니다. 역할을 나눠 맡기고 결과를 합칩니다.",
        code = """
            // defaultSystem — 이 ChatClient 로 나가는 모든 호출에 붙는 고정 시스템 메시지
            @Bean fun 작성자(b: ChatClient.Builder) = b.defaultSystem("너는 초안을 쓴다").build()
            @Bean fun 검토자(b: ChatClient.Builder) = b.defaultSystem("너는 초안을 비판한다").build()

            // 누가 먼저 말하고 몇 번 주고받을지는 직접 짠다
            val draft  = 작성자.prompt().user(주제).call().content()
            val review = 검토자.prompt().user(draft).call().content()
        """.trimIndent(),
        diagram = """
            flowchart LR
                T[주제] --> M1((작성자))
                M1 --> D[초안]
                D --> M2((검토자))
                M2 --> R[고칠 점]
        """.trimIndent(),
        defaultInput = "환불 안내 문구",
        model = "qwen3:4b-instruct",
    )

    override fun run(ctx: RunContext): RunResult {
        val draft = ctx.step("① 작성자") {
            it.system("너는 고객 안내 문구를 쓰는 작성자다. 두 문장으로 쓴다.").user(ctx.input).call().content()
        }
        val review = ctx.step("② 검토자") {
            it.system("너는 문구를 비판하는 검토자다. 고칠 점 한 가지만 짧게 말한다.").user(draft.orEmpty()).call().content()
        }
        ctx.note("빈은 둘, 순서와 종료는 코드가 정했습니다 — 조율 클래스는 없습니다")
        return ctx.result("초안 — $draft\n\n검토 — $review")
    }
}
