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
class StrategyGraphTest(val promptExecutor: PromptExecutor) : FunSpec({
    extensions(SpringExtension)

    fun router() = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = KoogModels.QWEN3_4B_INSTRUCT,
        strategy = dispatchRouter(
            askSkill = { "skill: $it" },
            askAssistant = { "assistant: $it" },
        ),
    )

    test("판교 사투리 요청은 스킬 담당에게 간다") {
        router().run("판교 사투리 3으로 바꿔 줘: 내일 회식 오세요") shouldBe
            "skill: 판교 사투리 3으로 바꿔 줘: 내일 회식 오세요"
    }

    test("나머지 요청은 비서에게 간다") {
        router().run("내일 일정 알려줘") shouldBe "assistant: 내일 일정 알려줘"
    }

    test("전략에서 Mermaid 다이어그램이 나온다") {
        val diagram = dispatchRouter({ it }, { it }).asMermaidDiagram()
        println("StrategyGraphTest 다이어그램:\n$diagram")
        diagram shouldContain "skill"
        diagram shouldContain "assistant"
    }
})
