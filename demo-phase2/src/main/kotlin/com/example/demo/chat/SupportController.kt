package com.example.demo.chat

import com.example.demo.axis.RagStore
import com.example.demo.support.Order
import com.example.demo.support.OrderService
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 주문 상담 봇.
 *
 * 기능별 버튼이 아니라 **대화 하나**다. 말을 거는 동안 Tool Calling·RAG·대화 메모리·
 * 커스텀 Advisor 가 한 파이프라인에서 같이 돈다. 무엇이 일어났는지는 응답에 실려 오는
 * 추적 정보로 화면에 펼친다.
 */
@RestController
@RequestMapping("/api/support")
class SupportController(
    builder: ChatClient.Builder,
    private val orders: OrderService,
    private val rag: RagStore,
) {

    private val chatClient = builder.build()
    private val memory: ChatMemory = MessageWindowChatMemory.builder().maxMessages(20).build()

    @GetMapping("/orders")
    fun orders(): List<Order> = orders.all()

    @PostMapping("/chat")
    fun chat(@RequestBody req: ChatRequest): ChatReply {
        val tools = SupportTools(orders)
        val trace = TraceAdvisor()
        val started = System.nanoTime()

        val reply = runCatching {
            chatClient.prompt()
                .system(SYSTEM)
                .user(req.message)
                .options(ChatOptions.builder().model(req.model))
                .tools(tools)
                .advisors(
                    MessageChatMemoryAdvisor.builder(memory).build(),
                    QuestionAnswerAdvisor.builder(rag.store)
                        .searchRequest(SEARCH)
                        .build(),
                    trace,
                )
                .advisors { it.param(ChatMemory.CONVERSATION_ID, req.conversationId) }
                .call()
                .content()
        }

        return ChatReply(
            ok = reply.isSuccess,
            text = reply.getOrNull().orEmpty().ifBlank { reply.exceptionOrNull()?.toShortMessage() ?: "(빈 응답)" },
            elapsedMs = (System.nanoTime() - started) / 1_000_000,
            pendingCancel = tools.pendingCancel,
            toolCalls = tools.calls,
            promptSent = trace.prompt,
            retrieved = retrieved(req.message),
        )
    }

    /** 승인 버튼이 부르는 자리. **LLM 은 여기 관여하지 않는다.** */
    @PostMapping("/approve")
    fun approve(@RequestBody req: ApproveRequest): ApproveReply {
        orders.cancelBlockedReason(req.orderId)?.let { return ApproveReply(false, it, orders.all()) }
        val cancelled = orders.cancel(req.orderId)
            ?: return ApproveReply(false, "주문번호 ${req.orderId} 를 찾을 수 없습니다.", orders.all())
        return ApproveReply(true, "주문 ${cancelled.id} 이 취소되었습니다. 환불은 3~5영업일 소요됩니다.", orders.all())
    }

    /** 유사도 점수를 함께 보여준다. 점수가 서로 붙어 있으면 임계값으로 거를 수 없다. */
    private fun retrieved(query: String): List<String> =
        runCatching {
            rag.store.similaritySearch(SearchRequest.from(SEARCH).query(query).build())
                .orEmpty()
                .map { "%.3f · %s".format(it.score ?: 0.0, it.text.orEmpty()) }
        }.getOrElse { emptyList() }

    private fun Throwable.toShortMessage() = "${this::class.simpleName}: ${message?.take(300)}"

    private companion object {
        /**
         * 유사도 임계값이 핵심이다. 기본 설정은 **무관한 문서도 topK 를 채워 무조건 주입**한다.
         * 주문 상태를 묻는데 장비 대여 규정이 컨텍스트로 들어가면 모델이 그쪽에 끌려가
         * 도구를 부르지 않고 엉뚱한 답을 한다(실제로 겪었다).
         *
         * 라우팅으로 RAG 를 통째로 건너뛰는 방법도 있지만, 그쪽은 분류에 LLM 호출을
         * 한 번 더 쓴다. 임계값은 추가 비용이 없다.
         */
        val SEARCH: SearchRequest = SearchRequest.builder()
            .topK(3)
            .similarityThreshold(0.5)
            .build()

        val SYSTEM = """
            너는 쇼핑몰 고객 상담원이다. 한국어로 간결하게 답한다.

            - 주문 상태를 물으면 반드시 도구로 조회한다. 추측하지 않는다.
            - 환불·교환·대여 규정은 주어진 사내 문서에 근거해서만 답한다. 문서에 없으면 모른다고 한다.
            - 취소 요청은 requestCancellation 도구를 부르고, 그 결과를 고객에게 그대로 안내한다.
              네가 직접 취소를 확정했다고 말하지 마라. 승인은 고객이 버튼으로 한다.
        """.trimIndent()
    }
}

data class ChatRequest(
    val message: String,
    val model: String,
    val conversationId: String = "support-1",
)

data class ChatReply(
    val ok: Boolean,
    val text: String,
    val elapsedMs: Long,
    /** 값이 있으면 화면이 승인/거부 버튼을 띄운다. */
    val pendingCancel: String?,
    val toolCalls: List<String>,
    val promptSent: String?,
    val retrieved: List<String>,
)

data class ApproveRequest(val orderId: String)

data class ApproveReply(val ok: Boolean, val message: String, val orders: List<Order>)
