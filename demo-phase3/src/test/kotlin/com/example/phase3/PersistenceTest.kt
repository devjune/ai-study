@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.example.phase3

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.isTombstone
import ai.koog.agents.snapshot.providers.file.JVMFilePersistenceStorageProvider
import ai.koog.prompt.executor.model.PromptExecutor
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeoutOrNull
import org.springframework.boot.test.context.SpringBootTest
import java.nio.file.Files

@SpringBootTest
class PersistenceTest(
    val promptExecutor: PromptExecutor,
    val historyProvider: ChatHistoryProvider,
) : FunSpec({
    extensions(SpringExtension)

    val checkpoints = JVMFilePersistenceStorageProvider(Files.createTempDirectory("koog-checkpoints"))

    fun assistantAgent(calendar: ToolSet, llmCalls: MutableList<Int>) = AIAgent(
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
        install(Persistence) {
            storage = checkpoints
            enableAutomaticPersistence = true
        }
        install(EventHandler) {
            onLLMCallStarting { ctx -> llmCalls += ctx.prompt.messages.size }
        }
    }

    test("세션마다 노드 단위 체크포인트가 남고 완료 때 tombstone이 남는다") {
        val session = "persist-${System.nanoTime()}"
        val calls = mutableListOf<Int>()

        val result = assistantAgent(tempCalendar(), calls).run("내일 일정 알려줘", session)

        val saved = checkpoints.getCheckpoints(session).sortedBy { it.createdAt }
        saved.forEach {
            println("PersistenceTest 체크포인트: node=${it.graphProperties?.nodePath} tombstone=${it.isTombstone()} messages=${it.messageHistory.size}")
        }
        println("PersistenceTest 응답: $result / LLM 호출 메시지 수: $calls")
        saved.mapNotNull { it.graphProperties?.nodePath?.substringAfterLast('/') } shouldBe
            listOf("nodeCallLLM", "nodeExecuteTool", "nodeSendToolResult")
        saved.last().isTombstone() shouldBe true
    }

    test("중단된 세션은 다음 run이 체크포인트에서 이어받는다") {
        val session = "persist-${System.nanoTime()}"
        val firstCalls = mutableListOf<Int>()
        withTimeoutOrNull(8_000) {
            assistantAgent(StuckCalendarToolSet(), firstCalls).run("내일 일정 알려줘", session)
        }
        val pending = checkpoints.getLatestCheckpoint(session)
        println("PersistenceTest 중단 시점: node=${pending?.graphProperties?.nodePath} tombstone=${pending?.isTombstone()} / 첫 실행 LLM 호출: $firstCalls")

        val calendar = tempCalendar()
        val secondCalls = mutableListOf<Int>()
        val result = assistantAgent(calendar, secondCalls).run("다시 시작", session)

        println("PersistenceTest 이어받은 응답: $result / LLM 호출 메시지 수: $secondCalls / 도구 호출: ${calendar.calls}")
        pending?.graphProperties?.nodePath?.substringAfterLast('/') shouldBe "nodeCallLLM"
        pending?.isTombstone() shouldBe false
        result shouldContain "팀 싱크"
        calendar.calls shouldBe listOf("내일")
        secondCalls.size shouldBe 1
        checkpoints.getLatestCheckpoint(session)?.isTombstone() shouldBe true
    }
})

@LLMDescription("캘린더")
class StuckCalendarToolSet : ToolSet {
    @Tool
    @LLMDescription("날짜의 일정 목록을 돌려준다")
    suspend fun schedules(@LLMDescription("날짜, 예: 내일") day: String): String = awaitCancellation()
}
