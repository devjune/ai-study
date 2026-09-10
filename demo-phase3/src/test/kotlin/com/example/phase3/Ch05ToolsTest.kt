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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class Ch05ToolsTest {
    @Autowired
    lateinit var promptExecutor: PromptExecutor

    @Test
    fun `에이전트가 도구를 호출해 답한다`() = runBlocking {
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
        println("Ch05ToolsTest 응답: $result")
        println("Ch05ToolsTest 도구 호출: ${tools.calls}")
        assertTrue(result.contains("1385") || result.contains("1,385"))
    }

    @Test
    fun `도구 호출 왕복을 그래프로 직접 그린다`() = runBlocking {
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
        println("Ch05ToolsTest 그래프 응답: $result")
        assertEquals(listOf("JPY"), tools.calls)
    }

    @Test
    fun `ToolSet이 함수 하나를 도구 스키마로 바꾼다`() {
        val descriptor = ExchangeToolSet().asTools().single().descriptor

        assertEquals("exchangeRate", descriptor.name)
        assertEquals("통화 코드의 원화 환율을 돌려준다", descriptor.description)
        assertEquals(listOf("code"), descriptor.requiredParameters.map { it.name })
        assertEquals("통화 코드, 예: USD", descriptor.requiredParameters.single().description)
    }
}

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
