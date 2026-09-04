package com.example.demo.compare

import com.example.demo.axis.OrderTools
import com.example.demo.axis.RagStore
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.stereotype.Component

/**
 * 라우팅 워크플로 — 분류 한 번, 처리 한 번.
 *
 * 다른 축은 도구나 RAG 를 **무조건** 얹는다. 실제 서비스에서는 낭비다. 주문 조회에
 * 사내 문서를 검색할 이유가 없고, 일반 질문에 도구 목록을 프롬프트에 넣을 이유도 없다.
 * 라우터가 먼저 분류하고 필요한 경로만 태운다.
 *
 * Spring AI 는 `Workflow` 같은 클래스를 주지 않는다. 재료는 [ChatClient] 하나뿐이고,
 * 분류 결과를 타입으로 받는 `.entity()` 가 분기의 축이 된다.
 */
@Component
class RoutingWorkflow(builder: ChatClient.Builder, private val rag: RagStore) {

    private val chatClient = builder.build()

    /** 반환은 (응답, 노트). 노트에 라우터의 판단과 실제 경로가 남는다. */
    fun run(model: String, prompt: String): Pair<String?, String> {
        val decision = classify(model, prompt)
        val request = chatClient.prompt().user(prompt).options(ChatOptions.builder().model(model))

        return when (decision.route()) {
            Route.ORDER -> {
                val tools = OrderTools()
                val reply = request.tools(tools).call().content()
                val used = if (tools.calls.isEmpty()) "도구 미호출" else "도구 ${tools.calls.size}회"
                reply to note(decision, "주문 조회 경로 · $used")
            }

            Route.POLICY -> {
                val reply = request.advisors(QuestionAnswerAdvisor.builder(rag.store).build())
                    .call().content()
                reply to note(decision, "사내 문서 검색 경로 · ${rag.store.similaritySearch(prompt).orEmpty().size}건 참조")
            }

            Route.GENERAL ->
                request.call().content() to note(decision, "일반 경로 · 도구·검색 없이 처리")
        }
    }

    private fun classify(model: String, prompt: String): RouteDecision =
        chatClient.prompt()
            .system(CLASSIFIER)
            .user(prompt)
            .options(ChatOptions.builder().model(model))
            .call()
            .entity(RouteDecision::class.java)
            ?: RouteDecision("general", "분류 실패 — 기본 경로")

    private fun note(decision: RouteDecision, taken: String) =
        "라우터 판단: ${decision.route} (${decision.reason.take(60)}) → $taken"

    private companion object {
        val CLASSIFIER = """
            너는 고객 문의를 분류하는 라우터다. 질문에 답하지 말고 분류만 해라.

            - order   : 주문번호·배송 상태 등 주문 조회 시스템을 봐야 하는 질문
            - policy  : 환불·교환·장비 대여 등 사내 규정 문서를 찾아야 하는 질문
            - general : 위 둘에 해당하지 않는 일반 질문

            route 에는 order, policy, general 중 하나만 적는다.
            reason 에는 그렇게 판단한 근거를 한국어 한 문장으로 적는다.
        """.trimIndent()
    }
}

enum class Route { ORDER, POLICY, GENERAL }

/**
 * 라우터의 분류 결과. 모델이 `route` 에 엉뚱한 값을 넣을 수 있어 [route] 로 좁힌다.
 * 못 알아들은 값은 GENERAL 로 떨어뜨린다 — 도구도 검색도 안 타는 가장 안전한 경로다.
 */
data class RouteDecision(val route: String, val reason: String) {
    fun route(): Route =
        Route.entries.firstOrNull { it.name.equals(route.trim(), ignoreCase = true) } ?: Route.GENERAL
}
