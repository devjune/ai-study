package com.example.phase3

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.executor.model.PromptExecutor
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.or
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.contain
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ToolsTest(val promptExecutor: PromptExecutor) : FunSpec({
    extensions(SpringExtension)

    test("에이전트가 도구를 호출해 답한다") {
        val tools = ExchangeToolSet()
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            systemPrompt = "환율 질문에는 반드시 exchangeRate 도구를 호출한다. " +
                "도구가 돌려준 숫자를 그대로 답에 싣고, 직접 계산하거나 추측하지 않는다.",
            temperature = 0.0,
            toolRegistry = ToolRegistry { tools(tools) },
        )
        val result = agent.run("1달러는 몇 원이야?")
        println("ToolsTest 응답: $result")
        println("ToolsTest 도구 호출: ${tools.calls}")
        result should (contain("1385") or contain("1,385"))
    }

    test("도구 호출 왕복을 그래프로 직접 그린다") {
        val toolLoop = strategy<String, String>("tool-loop") {
            val request by nodeLLMRequest()
            val execute by nodeExecuteTools()
            val report by nodeLLMSendToolResults()

            edge(nodeStart forwardTo request)
            edge(request forwardTo execute onToolCalls { true })
            edge(request forwardTo nodeFinish onTextMessage { true })
            edge(execute forwardTo report)
            edge(report forwardTo nodeFinish onTextMessage { true })
        }
        val tools = ExchangeToolSet()
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            strategy = toolLoop,
            systemPrompt = "환율 질문에는 반드시 exchangeRate 도구를 호출한다.",
            temperature = 0.0,
            toolRegistry = ToolRegistry { tools(tools) },
        )
        val result = agent.run("1엔은 몇 원이야?")
        println("ToolsTest 그래프 응답: $result")
        tools.calls shouldBe listOf("JPY")
    }

    test("비서가 캘린더 도구로 일정을 답한다") {
        val calendar = CalendarToolSet()
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            systemPrompt = "일정 질문에는 반드시 schedules 도구를 호출하고, " +
                "도구가 돌려준 일정만 그대로 답한다. 지어내지 않는다.",
            temperature = 0.0,
            toolRegistry = ToolRegistry { tools(calendar) },
        )
        val result = agent.run("내일 일정 알려줘")
        println("ToolsTest 캘린더 응답: $result")
        println("ToolsTest 캘린더 호출: ${calendar.calls}")
        result shouldContain "디자인 리뷰"
        calendar.calls.size shouldBe 1
    }

    test("ToolSet이 함수 하나를 도구 스키마로 바꾼다") {
        val descriptor = ExchangeToolSet().asTools().single().descriptor

        descriptor.name shouldBe "exchangeRate"
        descriptor.description shouldBe "통화 코드의 원화 환율을 돌려준다"
        descriptor.requiredParameters.map { it.name } shouldBe listOf("code")
        descriptor.requiredParameters.single().description shouldBe "통화 코드, 예: USD"
    }
})

@LLMDescription("환율 조회 도구")
class ExchangeToolSet : ToolSet {
    val calls = mutableListOf<String>()

    @Tool
    @LLMDescription("통화 코드의 원화 환율을 돌려준다")
    fun exchangeRate(@LLMDescription("통화 코드, 예: USD") code: String): String {
        calls += code
        return when (code.uppercase()) {
            "USD" -> "1385"
            "JPY" -> "9.2"
            else -> "미지원 통화"
        }
    }
}
