package com.example.phase3

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class Ch04StrategyTest {
    @Autowired
    lateinit var promptExecutor: PromptExecutor

    @Test
    fun `두 노드를 잇는 커스텀 전략이 돈다`() = runBlocking {
        val chain = strategy<String, String>("two-step") {
            val draft by nodeLLMRequest()
            val polish by nodeLLMRequest()

            edge(nodeStart forwardTo draft)
            edge(draft forwardTo polish transformed { "그 숫자에 10을 더하면?" })
            edge(polish forwardTo nodeFinish transformed { it.textContent() })
        }
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            strategy = chain,
            systemPrompt = "숫자만 답한다. 다른 말은 붙이지 않는다.",
            temperature = 0.2,
        )
        val result = agent.run("2 더하기 3은?")
        println("Ch04StrategyTest 체인 응답: $result")
        assertTrue(result.contains("15"))
    }

    @Test
    fun `조건 엣지가 분기를 고른다`() = runBlocking {
        val router = strategy<String, String>("route") {
            val classify by nodeLLMRequest()
            val math by node<String, String>("math") { "수학 경로" }
            val other by node<String, String>("other") { "일반 경로" }

            edge(nodeStart forwardTo classify)
            edge(classify forwardTo math onTextMessage { "MATH" in it.text })
            edge(classify forwardTo other onTextMessage { true })
            edge(math forwardTo nodeFinish)
            edge(other forwardTo nodeFinish)
        }
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            strategy = router,
            systemPrompt = "질문이 계산이면 MATH, 아니면 OTHER 한 단어만 답한다.",
            temperature = 0.2,
        )
        val result = agent.run("2 더하기 3은?")
        println("Ch04StrategyTest 라우팅 응답: $result")
        assertEquals("수학 경로", result)
    }

    @Test
    fun `자기 자신으로 돌아오는 엣지가 루프가 된다`() = runBlocking {
        val loop = strategy<String, String>("loop") {
            val step by node<String, String>("step") { "$it." }

            edge(nodeStart forwardTo step)
            edge(step forwardTo step onCondition { it.length < 3 })
            edge(step forwardTo nodeFinish onCondition { it.length >= 3 })
        }
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            strategy = loop,
            maxIterations = 5,
        )
        assertEquals("...", agent.run(""))
    }
}
