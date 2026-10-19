package com.example.phase3

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.asMermaidDiagram
import ai.koog.prompt.executor.model.PromptExecutor
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class DispatchTest(val promptExecutor: PromptExecutor) : FunSpec({
    extensions(SpringExtension)

    fun agent() = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = KoogModels.QWEN3_4B_INSTRUCT,
        strategy = dispatchRouter(),
    )

    test("스킬 요청을 가른다") {
        agent().run("판교 사투리 3으로 바꿔 줘: 내일 회식 오세요") shouldBe "SKILL"
    }

    test("비서 요청을 가른다") {
        agent().run("내일 일정 알려줘") shouldBe "TASK"
    }

    test("가르는 전략에서 다이어그램이 나온다") {
        val diagram = dispatchRouter().asMermaidDiagram()
        println("DispatchTest 다이어그램:\n$diagram")
        diagram shouldContain "skill"
        diagram shouldContain "task"
    }
})
