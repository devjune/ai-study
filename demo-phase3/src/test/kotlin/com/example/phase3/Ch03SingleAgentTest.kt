package com.example.phase3

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class Ch03SingleAgentTest {
    @Autowired
    lateinit var promptExecutor: PromptExecutor

    @Test
    fun `AIAgent 한 개가 Ollama로 답한다`() = runBlocking {
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            systemPrompt = "한 문장으로만 답한다.",
        )
        val result = agent.run("Spring AI가 무엇인지 한 문장으로")
        println("Ch03SingleAgentTest 응답: $result")
        assertTrue(result.isNotBlank())
    }
}
