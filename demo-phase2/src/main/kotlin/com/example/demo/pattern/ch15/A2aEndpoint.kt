package com.example.demo.pattern.ch15

import com.example.demo.pattern.PatternSpec
import com.example.demo.pattern.SupportTools
import com.example.demo.support.OrderService
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * 15장 — **원격 에이전트** 쪽. Spring AI 2.0.1 에는 A2A 구현이 없어 규격의 최소 조각을 직접 얹었다.
 *
 * 두 엔드포인트가 전부다. 하나는 자기가 뭘 할 수 있는지 밝히는 신분증(Agent Card),
 * 하나는 일을 받아 처리하는 자리. 클라이언트는 이 둘만 알면 된다.
 */
@RestController
class A2aEndpoint(builder: ChatClient.Builder, private val orderService: OrderService) {

    private val chatClient = builder.build()

    /**
     * 약속된 자리에 걸어 두는 신분증. 주소·버전·할 수 있는 일이 적혀 있다.
     * 브라우저로 열어 봐도 된다 — http://localhost:8080/.well-known/agent.json
     */
    @GetMapping("/.well-known/agent.json")
    fun agentCard(): Map<String, Any> = mapOf(
        "name" to "OrderSupportAgent",
        "description" to "주문 상태를 확인해 고객에게 안내한다",
        "url" to "http://localhost:8080/a2a",
        "version" to "1.0.0",
        "capabilities" to mapOf("streaming" to false, "pushNotifications" to false),
        "defaultInputModes" to listOf("text"),
        "defaultOutputModes" to listOf("text"),
        "skills" to listOf(
            mapOf(
                "id" to "order_status",
                "name" to "Order Status",
                "description" to "주문 번호를 받아 배송 상태를 안내한다",
                "examples" to listOf("A-1024 지금 어디쯤 왔어?"),
                "tags" to listOf("order", "shipping"),
            ),
        ),
    )

    /**
     * 일을 받는 자리. 봉투는 JSON-RPC 다.
     *
     * 안에서 무엇을 하는지는 **밖으로 새지 않는다.** 여기서는 모델을 부르고 도구까지 쓰지만,
     * 클라이언트가 받는 것은 작업 상태와 결과물뿐이다.
     */
    @PostMapping("/a2a")
    fun send(@RequestBody req: Map<String, Any?>): Map<String, Any?> {
        val id = req["id"]
        val params = req["params"] as? Map<*, *>
        val text = ((params?.get("message") as? Map<*, *>)?.get("parts") as? List<*>)
            ?.filterIsInstance<Map<*, *>>()?.firstNotNullOfOrNull { it["text"] as? String }
            ?: return error(id, "message.parts 에 text 가 없습니다")

        val taskId = "task-" + UUID.randomUUID().toString().take(8)
        val history = mutableListOf(state("submitted"), state("working"))

        // 상대가 알아서 하는 부분. 모델을 부르든 도구를 쓰든 클라이언트는 모른다
        val answer = runCatching {
            chatClient.prompt()
                .options(ChatOptions.builder().model(PatternSpec.DEFAULT_MODEL).maxTokens(160))
                .system("주문 상담원이다. 도구로 확인한 사실만 한국어 두 문장 안으로 안내한다.")
                .user(text)
                .tools(SupportTools(orderService))
                .call().content()
        }.getOrNull()

        val done = if (answer.isNullOrBlank()) "failed" else "completed"
        history += state(done)

        return mapOf(
            "jsonrpc" to "2.0",
            "id" to id,
            "result" to mapOf(
                "id" to taskId,
                "contextId" to (params?.get("contextId") ?: UUID.randomUUID().toString().take(8)),
                "kind" to "task",
                "status" to history.last(),
                "history" to history,
                "artifacts" to listOf(
                    mapOf(
                        "name" to "answer",
                        "parts" to listOf(mapOf("kind" to "text", "text" to answer.orEmpty())),
                    ),
                ),
            ),
        )
    }

    private fun state(name: String) = mapOf("state" to name, "timestamp" to Instant.now().toString())

    private fun error(id: Any?, message: String) =
        mapOf("jsonrpc" to "2.0", "id" to id, "error" to mapOf("code" to -32602, "message" to message))
}
