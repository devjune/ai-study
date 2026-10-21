package com.example.phase3

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.ai.chat.memory.ChatMemoryRepository
import org.springframework.ai.chat.messages.MessageType
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ChatMemoryTest(
    val promptExecutor: PromptExecutor,
    val historyProvider: ChatHistoryProvider,
    val chatMemoryRepository: ChatMemoryRepository,
) : FunSpec({
    extensions(SpringExtension)

    fun assistantAgent(calendar: CalendarToolSet) = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = KoogModels.QWEN3_4B_INSTRUCT,
        systemPrompt = "비서다. 짧게 답한다. " +
            "일정 질문에는 schedules 도구를 호출하고 그 결과만 답한다.",
        temperature = 0.0,
        toolRegistry = ToolRegistry { tools(calendar) },
    ) {
        install(ChatMemory) {
            chatHistoryProvider = historyProvider
        }
    }

    test("같은 세션이면 새로 만든 에이전트도 앞 대화를 이어받는다") {
        val calendar = tempCalendar()
        val session = "memory-${System.nanoTime()}"

        assistantAgent(calendar).run("내일 일정 알려줘", session)
        val result = assistantAgent(calendar).run("아까 말한 첫 번째 일정이 뭐였지?", session)

        println("ChatMemoryTest 응답: $result")
        result shouldContain "팀 싱크"
        calendar.calls shouldBe listOf("내일")
    }

    test("이력은 세션 ID별로 따로 저장된다") {
        val sessionA = "memory-a-${System.nanoTime()}"
        val sessionB = "memory-b-${System.nanoTime()}"

        assistantAgent(tempCalendar()).run("내일 일정 알려줘", sessionA)

        val storedA = chatMemoryRepository.findByConversationId(sessionA)
        storedA.forEach { println("ChatMemoryTest 저장된 행: ${it.messageType} | ${it.text}") }
        storedA.last().messageType shouldBe MessageType.ASSISTANT
        storedA.last().text shouldContain "팀 싱크"
        chatMemoryRepository.findByConversationId(sessionB) shouldBe emptyList()
    }
})
