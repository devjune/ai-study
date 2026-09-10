package com.example.phase3

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.memory.ChatMemoryRepository
import org.springframework.ai.chat.messages.MessageType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class Ch06MemoryTest {
    @Autowired
    lateinit var promptExecutor: PromptExecutor

    @Autowired
    lateinit var historyProvider: ChatHistoryProvider

    @Autowired
    lateinit var chatMemoryRepository: ChatMemoryRepository

    @Test
    fun `이전 대화를 기억한다`() = runBlocking {
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            systemPrompt = "한 문장으로만 답한다.",
            temperature = 0.0,
        ) {
            install(ChatMemory) {
                chatHistoryProvider = historyProvider
            }
        }
        val sessionId = "ch06-이름"

        agent.run("내 이름은 준이야", sessionId)
        val result = agent.run("내 이름이 뭐라고 했지?", sessionId)

        println("Ch06MemoryTest 응답: $result")
        assertTrue(result.contains("준"))
    }

    @Test
    fun `저장된 것은 평문뿐이고 도구 호출은 남지 않는다`() = runBlocking {
        val tools = ExchangeToolSet()
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            systemPrompt = "환율 질문에는 반드시 exchangeRate 도구를 호출한다. " +
                "도구가 돌려준 숫자를 그대로 답에 싣고, 직접 계산하거나 추측하지 않는다.",
            temperature = 0.0,
            toolRegistry = ToolRegistry { tools(tools) },
        ) {
            install(ChatMemory) {
                chatHistoryProvider = historyProvider
            }
        }
        val sessionId = "ch06-도구"

        agent.run("1달러는 몇 원이야?", sessionId)

        val stored = chatMemoryRepository.findByConversationId(sessionId)
        stored.forEach { println("Ch06MemoryTest 저장된 행: ${it.messageType} | ${it.text}") }

        assertEquals(listOf("USD"), tools.calls)
        assertEquals(
            listOf(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT),
            stored.map { it.messageType },
        )
        assertTrue("1385" in stored.last().text)
    }

    @Test
    fun `압축 노드가 이력을 접고 대화는 이어진다`() = runBlocking {
        val compressing = strategy<String, String>("compress") {
            val ask by nodeLLMRequest()
            val compress by nodeLLMCompressHistory<Message.Assistant>()
            val recall by nodeLLMRequest()

            edge(nodeStart forwardTo ask)
            edge(ask forwardTo compress)
            edge(compress forwardTo recall transformed { "내 이름이 뭐라고 했지?" })
            edge(recall forwardTo nodeFinish transformed { it.textContent() })
        }
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            strategy = compressing,
            systemPrompt = "한 문장으로만 답한다.",
            temperature = 0.0,
        )

        val result = agent.run("내 이름은 준이야")

        println("Ch06MemoryTest 압축 후 응답: $result")
        assertTrue(result.contains("준"))
    }
}
