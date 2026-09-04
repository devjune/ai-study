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
 * 모델은 요청마다 포터블 [ChatOptions] 로 지정한다 — 벤더 전용 옵션이 아니라
 * 공통 추상화라, 나중에 Ollama 를 OpenAI 호환 사내 서버로 바꿔도 이 코드는
 * 그대로다. ChatClient 는 하나뿐이고 재시작도 필요 없다.
 *
 * 2.0 에서 `options()` 는 빌드된 인스턴스가 아니라 **빌더**를 받는다.
 */
@Component
class AxisRunner(
    builder: ChatClient.Builder,
    private val ragStore: RagStore,
) {

    private val chatClient = builder.build()

    fun run(axis: Axis, model: String, prompt: String): RunResult {
        val started = System.nanoTime()
        return runCatching { execute(axis, model, prompt) }
            .fold(
                onSuccess = { (output, note) ->
                    result(model, axis, true, started, output = output, note = note)
                },
                onFailure = { e ->
                    result(model, axis, false, started, error = describe(e))
                },
            )
    }

    private fun execute(axis: Axis, model: String, prompt: String): Pair<String?, String?> =
        when (axis) {
            Axis.CHAT -> chatClient.prompt().user(prompt).options(options(model))
                .call().content() to null

            Axis.STRUCTURED -> {
                val movie = chatClient.prompt().user(prompt).options(options(model))
                    .call().entity(Movie::class.java)
                render(movie) to "스키마 준수 — Movie 타입으로 역직렬화됨"
            }

            Axis.TOOL -> {
                val tools = OrderTools()
                val reply = chatClient.prompt().user(prompt).options(options(model))
                    .tools(tools)
                    .call().content()
                reply to toolNote(tools)
            }

            Axis.RAG -> {
                val store = ragStore.ready()
                val reply = chatClient.prompt().user(prompt).options(options(model))
                    .advisors(QuestionAnswerAdvisor.builder(store).build())
                    .call().content()
                reply to retrievedNote(prompt)
            }
        }

    /** 포터블 옵션. 벤더 전용 클래스를 쓰지 않는 것이 이 데모의 요점이다. */
    private fun options(model: String) = ChatOptions.builder().model(model)

    /** 문자열이 아니라 타입으로 들어왔음을 필드 단위로 보여준다. */
    private fun render(movie: Movie?): String =
        movie?.let {
            """
            title    : ${it.title}
            year     : ${it.year}
            director : ${it.director}
            genres   : ${it.genres.joinToString(", ")}
            """.trimIndent()
        } ?: "null"

    private fun toolNote(tools: OrderTools): String =
        if (tools.calls.isEmpty()) {
            "도구를 호출하지 않음 — 답이 그럴듯해도 근거 없이 지어낸 것이다"
        } else {
            "도구 호출 ${tools.calls.size}회 — ${tools.calls.joinToString(", ")}"
        }

    /** 모델에 실제로 무엇이 주어졌는지. 답과 대조해야 참조 여부를 판단할 수 있다. */
    private fun retrievedNote(prompt: String): String =
        runCatching {
            ragStore.ready().similaritySearch(prompt)
                ?.take(2)
                ?.joinToString(" / ") { it.text?.take(60).orEmpty() + "…" }
                ?.let { "검색된 문서: $it" }
                ?: "검색 결과 없음"
        }.getOrElse { "검색 결과 확인 실패: ${it.message}" }

    private fun result(
        model: String,
        axis: Axis,
        ok: Boolean,
        startedNano: Long,
        output: String? = null,
        error: String? = null,
        note: String? = null,
    ) = RunResult(
        model = model,
        axis = axis.name.lowercase(),
        ok = ok,
        elapsedMs = (System.nanoTime() - startedNano) / 1_000_000,
        output = output,
        error = error,
        note = note,
    )

    /**
     * 실패 원인을 화면에 그대로 보여준다. 구조화 출력이 깨졌을 때 어떤 형태로
     * 깨졌는지가 학습 대상이라, 예외 메시지를 감추지 않는다.
     */
    private fun describe(e: Throwable): String =
        buildString {
            append(e::class.simpleName)
            e.message?.let { append(": ").append(it.take(600)) }
            e.cause?.takeIf { it !== e }?.let {
                append("\n  원인: ").append(it::class.simpleName).append(": ")
                append(it.message?.take(300).orEmpty())
            }
        }
}

enum class Axis { CHAT, STRUCTURED, TOOL, RAG }

data class Movie(
    val title: String,
    val year: Int,
    val director: String,
    val genres: List<String>,
)
