package com.example.phase3

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.RollbackToolRegistry
import ai.koog.agents.snapshot.providers.file.JVMFilePersistenceStorageProvider
import ai.koog.prompt.executor.model.PromptExecutor
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.test.context.SpringBootTest
import java.nio.file.Files

@SpringBootTest
class RollbackTest(val promptExecutor: PromptExecutor) : FunSpec({
    extensions(SpringExtension)

    val checkpoints = JVMFilePersistenceStorageProvider(Files.createTempDirectory("koog-rollback"))

    fun assistant(calendar: CalendarToolSet) = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = KoogModels.QWEN3_4B_INSTRUCT,
        systemPrompt = "비서다. 짧게 답한다. 일정 추가에는 add 도구를 호출하고 그 결과만 답한다.",
        temperature = 0.0,
        toolRegistry = ToolRegistry { tools(calendar) },
        strategy = assistantStrategy(calendar, checkpoints),
    ) {
        install(Persistence) {
            storage = checkpoints
            enableAutomaticPersistence = true
            rollbackToolRegistry = RollbackToolRegistry.builder()
                .registerRollback(calendar::add, calendar::remove).build()
        }
        install(EventHandler, trace("assistant"))
    }

    test("같은 시각과 겹치는 추가는 롤백되고 반대 도구 remove가 같은 인자로 불린다") {
        val calendar = tempCalendar()
        val result = assistant(calendar).run("내일 14시에 치과 추가해 줘", "rollback-${System.nanoTime()}")

        println("RollbackTest 응답: $result / 호출: ${calendar.calls}")
        result shouldContain "추가하지 않았습니다"
        calendar.calls.filter { it.startsWith("remove:") } shouldBe listOf("remove:내일 14:00 치과")
        calendar.schedules("내일") shouldBe "10:00 팀 싱크, 14:00 디자인 리뷰"
    }

    test("겹치지 않는 추가는 그대로 남는다") {
        val calendar = tempCalendar()
        val result = assistant(calendar).run("내일 16시에 치과 추가해 줘", "rollback-${System.nanoTime()}")

        println("RollbackTest 응답: $result / 호출: ${calendar.calls}")
        calendar.calls.none { it.startsWith("remove:") } shouldBe true
        calendar.schedules("내일") shouldBe "10:00 팀 싱크, 14:00 디자인 리뷰, 16:00 치과"
    }
})
