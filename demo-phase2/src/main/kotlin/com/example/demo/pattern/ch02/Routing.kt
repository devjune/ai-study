package com.example.demo.pattern.ch02

import com.example.demo.pattern.*
import com.example.demo.support.OrderService
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.stereotype.Component

@Component
class Routing(private val orderService: OrderService, private val docs: Docs) : PatternRunner {
    override val spec = PatternSpec(
        2, "라우팅", "Routing",
        summary = "1장의 직선 체인에 조건 로직을 넣습니다. 의도는 모델이 판별하고, 어느 경로로 갈지는 그 판정을 받은 코드의 when 이 정합니다.",
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

            val request = chatClient.prompt().user(질문)   // 세 경로가 같은 요청에서 갈라진다

            when (decision.route()) {
                Route.ORDER   -> request.tools(SupportTools(orderService)).call().content()
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
                T --> M2((모델<br/>답))
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
        ctx.note("판정은 모델이, 분기는 코드의 when 이 — 주문 조회에 사내 문서를 검색할 이유가 없습니다")

        val answer = when (decision.route()) {
            Route.ORDER -> {
                val tools = SupportTools(orderService)
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

/** 모델이 채워 줄 그릇. 이 클래스로 JSON 스키마가 만들어져 프롬프트에 실린다. */
data class RouteDecision(val route: String, val reason: String) {
    /** 모델은 아무 문자열이나 넣을 수 있다. 못 알아본 값은 도구도 문서도 안 붙는 경로로 보낸다. */
    fun route(): Route =
        Route.entries.firstOrNull { it.name.equals(route.trim(), true) } ?: Route.GENERAL
}

enum class Route { ORDER, POLICY, GENERAL }
