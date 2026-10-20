package com.example.phase3

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.asMermaidDiagram
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.executor.model.PromptExecutor
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.test.context.SpringBootTest
import java.io.ByteArrayOutputStream
import java.io.PrintStream

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

    test("띄어쓰기 없는 판교사투리도 스킬 담당에게 간다") {
        router().run("판교사투리란?") shouldBe "skill: 판교사투리란?"
    }

    test("나머지 요청은 비서에게 간다") {
        router().run("내일 일정 알려줘") shouldBe "assistant: 내일 일정 알려줘"
    }

    test("분기 결과가 이벤트 줄로 찍힌다") {
        val out = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(out, true, "UTF-8"))
        try {
            AIAgent(
                promptExecutor = promptExecutor,
                llmModel = KoogModels.QWEN3_4B_INSTRUCT,
                strategy = dispatchRouter(askSkill = { "skill: $it" }, askAssistant = { "assistant: $it" }),
            ) {
                install(EventHandler, trace("dispatch", nodes = true))
            }.run("내일 일정 알려줘")
        } finally {
            System.setOut(original)
        }
        out.toString("UTF-8") shouldContain "dispatch    → assistant"
    }

    test("전략에서 Mermaid 다이어그램이 나온다") {
        val diagram = dispatchRouter({ it }, { it }).asMermaidDiagram()
        println("StrategyGraphTest 다이어그램:\n$diagram")
        diagram shouldContain "skill"
        diagram shouldContain "assistant"
    }
})
