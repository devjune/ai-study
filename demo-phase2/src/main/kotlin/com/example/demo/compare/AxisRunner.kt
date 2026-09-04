package com.example.demo.compare

import com.example.demo.axis.OrderTools
import com.example.demo.axis.RagStore
import com.example.demo.support.RunResult
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.stereotype.Component

/**
 * 시험 축 하나를 모델 하나로 실행한다.
 *
 * 모델은 요청마다 포터블 [ChatOptions] 로 지정한다 — 벤더 전용 옵션이 아니라 공통
 * 추상화라, Ollama 를 OpenAI 호환 사내 서버로 바꿔도 이 코드는 그대로다. ChatClient
 * 는 하나뿐이고 재시작도 필요 없다.
 *
 * 2.0 에서 `options()` 는 빌드된 인스턴스가 아니라 **빌더**를 받는다.
 */
@Component
class AxisRunner(
    builder: ChatClient.Builder,
    private val rag: RagStore,
    private val routing: RoutingWorkflow,
) {

    private val chatClient = builder.build()

    fun run(axis: Axis, model: String, prompt: String): RunResult {
        val started = System.nanoTime()
        return try {
            val (text, note) = execute(axis, model, prompt)
            RunResult(model, true, elapsedMs(started), text.orEmpty(), note)
        } catch (e: Exception) {
            RunResult(model, false, elapsedMs(started), "${e::class.simpleName}: ${e.message}")
        }
    }

    private fun execute(axis: Axis, model: String, prompt: String): Pair<String?, String?> {
        val request = chatClient.prompt().user(prompt).options(ChatOptions.builder().model(model))
        return when (axis) {
            Axis.CHAT -> request.call().content() to null

            Axis.STRUCTURED ->
                request.call().entity(Movie::class.java).toString() to "Movie 타입으로 역직렬화됨"

            Axis.TOOL -> {
                val tools = OrderTools()
                request.tools(tools).call().content() to toolNote(tools)
            }

            Axis.RAG ->
                request.advisors(QuestionAnswerAdvisor.builder(rag.store).build())
                    .call().content() to retrievedNote(prompt)

            Axis.ROUTING -> routing.run(model, prompt)
        }
    }

    /**
     * 도구를 안 불렀다고 지어낸 것은 아니다 — 답을 거절할 수도 있다.
     * 사실만 적고 판단은 사람에게 맡긴다.
     */
    private fun toolNote(tools: OrderTools) =
        if (tools.calls.isEmpty()) {
            "도구 미호출 — 답의 근거가 도구가 아니다. 지어냈는지 거절했는지 본문을 볼 것"
        } else {
            "도구 호출 ${tools.calls.size}회 — ${tools.calls.joinToString(", ")}"
        }

    /**
     * 모델에 무엇이 주어졌는지를 **유사도 순위와 함께** 보여준다. 정답 문서가 하위권인데
     * 답이 맞았다면 검색이 잘된 게 아니라 topK 가 넉넉해 딸려 온 것이고, 문서가 늘면 깨진다.
     */
    private fun retrievedNote(prompt: String): String {
        val docs = rag.store.similaritySearch(prompt).orEmpty()
        return docs.mapIndexed { i, d -> "${i + 1}) ${d.text?.take(40).orEmpty()}…" }
            .joinToString("  ", prefix = "검색 ${docs.size}건 — ")
            .ifBlank { "검색된 문서 없음" }
    }

    private fun elapsedMs(startedNano: Long) = (System.nanoTime() - startedNano) / 1_000_000
}

enum class Axis { CHAT, STRUCTURED, TOOL, RAG, ROUTING }

data class Movie(val title: String, val year: Int, val director: String, val genres: List<String>)
