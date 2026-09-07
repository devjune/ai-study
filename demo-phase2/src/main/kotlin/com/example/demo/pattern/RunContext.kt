package com.example.demo.pattern

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.AdvisorChain
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor
import org.springframework.ai.chat.evaluation.RelevancyEvaluator
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.ai.evaluation.EvaluationResponse

/** Advisor 는 작은 order 부터 실행된다. 추적은 프롬프트를 고치는 쪽보다 뒤에 서야 한다. */
const val GUARDRAIL_ORDER = 1000
const val TRACE_ORDER = 2000

/**
 * 모델에 나간 프롬프트를 **호출마다** 붙잡는다.
 *
 * 도구 호출 요청과 실행 결과는 메시지의 `text` 가 아니라 별도 필드에 있어서,
 * `text` 만 찍으면 왕복에서 가장 중요한 두 줄이 빈칸으로 나온다.
 *
 * `order` 를 크게 주어 도구 루프(`ToolCallingAdvisor`, `MIN_VALUE + 300`) **안쪽**에 선다.
 * 그래야 왕복마다 다시 불려 전부 잡힌다.
 *
 * 패턴이 다는 Advisor 보다도 뒤에 서야 한다. 18장 가드레일처럼 프롬프트를 고치는 Advisor 가
 * 있으면, 트레이서가 먼저 서는 순간 **고쳐지기 전** 프롬프트를 잡아 화면이 거짓말을 한다.
 */
class TraceAdvisor : BaseAdvisor {

    private val captured = mutableListOf<String>()
    val rounds: List<String> get() = captured

    override fun before(request: ChatClientRequest, chain: AdvisorChain): ChatClientRequest {
        captured += request.prompt().instructions.joinToString("\n\n", transform = ::render)
        return request
    }

    override fun after(response: ChatClientResponse, chain: AdvisorChain) = response

    override fun getOrder(): Int = TRACE_ORDER

    private fun render(message: Message): String = buildString {
        append("[${message.messageType}]")
        message.text?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
        when (message) {
            is AssistantMessage ->
                message.toolCalls.orEmpty().forEach {
                    append("\n▸ 도구 호출 요청  ${it.name()}(${it.arguments()})")
                }
            is ToolResponseMessage ->
                message.responses.forEach {
                    append("\n◂ 도구 실행 결과  ${it.name()} → ${it.responseData()}")
                }
            else -> Unit
        }
    }
}

/**
 * 패턴 하나를 실행하는 동안의 작업 공간.
 *
 * [step] 으로 감싼 호출만 타임라인에 기록된다. 화면에 싣는 코드에는 이 래퍼가 없다 —
 * 패턴의 알맹이를 가리지 않기 위해서다.
 */
class RunContext(
    builder: ChatClient.Builder,
    val input: String,
    val model: String,
    /** 이 실행에 걸 토큰 상한. 장에 따라 더 필요할 수 있다 */
    private val maxTokens: Int = MAX_TOKENS,
) {
    private val recorded = mutableListOf<ModelCall>()
    private val notes = mutableListOf<String>()

    val chatClient: ChatClient = builder.build()
    val builder: ChatClient.Builder = builder

    /** 여기까지 기록된 호출. 실행 도중 걸린 시간을 되짚어 볼 때 쓴다. */
    val calls: List<ModelCall> get() = recorded

    fun note(text: String) { notes.add(text) }

    /** 모델 호출 한 단계. 안에서 몇 왕복이 일어나든 왕복마다 하나씩 기록한다. */
    fun <T> step(label: String, block: (ChatClient.ChatClientRequestSpec) -> T): T {
        val trace = TraceAdvisor()
        val started = System.nanoTime()
        val result = block(
            chatClient.prompt()
                // maxTokens 는 포터블 옵션이다. Ollama 에서는 num_predict 로 매핑된다.
                // 시연에서 답이 길어지면 몇 분씩 기다리게 되므로 상한을 건다.
                .options(ChatOptions.builder().model(model).maxTokens(maxTokens))
                .advisors(trace),
        )
        val elapsed = (System.nanoTime() - started) / 1_000_000
        val rounds = trace.rounds.ifEmpty { listOf("(프롬프트를 잡지 못함)") }
        rounds.forEachIndexed { i, prompt ->
            recorded += ModelCall(
                seq = recorded.size + 1,
                step = if (rounds.size > 1) "$label ${i + 1}차" else label,
                prompt = prompt,
                elapsedMs = if (i == rounds.lastIndex) elapsed else 0,
            )
        }
        return result
    }

    /**
     * `Evaluator` 처럼 **자기 `ChatClient` 를 따로 만들어 쓰는** 부품의 호출도 타임라인에 남긴다.
     * 추적 Advisor 를 기본으로 단 빌더를 넘겨주면 그 안에서 일어난 호출이 잡힌다.
     */
    fun <T> external(label: String, block: (ChatClient.Builder) -> T): T {
        val trace = TraceAdvisor()
        val started = System.nanoTime()
        val result = block(
            builder.clone()
                .defaultAdvisors(trace)
                .defaultOptions(ChatOptions.builder().model(model).maxTokens(maxTokens)),
        )
        val elapsed = (System.nanoTime() - started) / 1_000_000
        trace.rounds.ifEmpty { listOf("(프롬프트를 잡지 못함)") }.forEachIndexed { i, prompt ->
            recorded += ModelCall(recorded.size + 1, if (trace.rounds.size > 1) "$label ${i + 1}" else label, prompt, if (i == 0) elapsed else 0)
        }
        return result
    }

    fun result(output: String?, ok: Boolean = true, elapsedMs: Long = 0, pendingCancel: String? = null) =
        RunResult(ok, output.orEmpty(), recorded.toList(), elapsedMs, notes.toList(), pendingCancel)

    companion object {
        /**
         * 시연용 기본 상한. 패턴의 모양을 보는 것이 목적이라 답이 길 이유가 없다.
         *
         * 다만 `.entity()` 로 목록을 받는 장은 JSON 이 끝나기 전에 잘리므로
         * [PatternSpec.maxTokens] 로 장별로 올린다.
         */
        const val MAX_TOKENS = 160
    }
}

/**
 * 채점기 호출. `Evaluator` 는 자기 `ChatClient` 를 만들어 쓰므로 [RunContext.external] 로
 * 감싸야 타임라인에 남는다. 작은 모델은 채점 형식을 못 맞출 때가 있어, 실패하면 null 이다.
 */
fun RunContext.evaluate(label: String, request: EvaluationRequest): EvaluationResponse? =
    runCatching {
        external(label) { RelevancyEvaluator.builder().chatClientBuilder(it).build().evaluate(request) }
    }.getOrNull()

/** `RelevancyEvaluator` 는 feedback 이 비어 있을 때가 있다. 점수라도 보여준다. */
fun EvaluationResponse.verdict(): String {
    val head = if (isPass) "통과" else "미달"
    val detail = feedback?.takeIf { it.isNotBlank() }?.take(70) ?: "점수 %.2f".format(score)
    return "$head · $detail"
}
