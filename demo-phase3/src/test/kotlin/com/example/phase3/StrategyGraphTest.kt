package com.example.phase3

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.asMermaidDiagram
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.prompt.executor.model.PromptExecutor
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class StrategyGraphTest(val promptExecutor: PromptExecutor) : FunSpec({
    extensions(SpringExtension)

    fun agent() = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = KoogModels.QWEN3_4B_INSTRUCT,
        strategy = assistantRouter(),
        systemPrompt = "비서다. 짧게 답한다.",
        temperature = 0.2,
    )

    test("조건 엣지가 분기를 고른다") {
        val result = agent().run("오늘 일정 알려줘")
        println("StrategyGraphTest 라우팅 응답: $result")
        result shouldContain "팀 싱크"
    }

    test("일반 질문은 모델이 받아 답한다") {
        val result = agent().run("오늘 점심 뭐 먹을까")
        println("StrategyGraphTest 일반 응답: $result")
        result.shouldNotBeBlank()
        result shouldNotContain "팀 싱크"
    }

    test("전략에서 Mermaid 다이어그램이 나온다") {
        val diagram = assistantRouter().asMermaidDiagram()
        println("StrategyGraphTest 다이어그램:\n$diagram")
        diagram shouldContain "stateDiagram"
        diagram shouldContain "schedule"
    }
})

private fun assistantRouter() = strategy<String, String>("route") {
    val schedule by node<String, String>("schedule") { CalendarToolSet().schedules(it) }
    val reply by nodeLLMRequest()

    edge(nodeStart forwardTo schedule onCondition { "일정" in it })
    edge(nodeStart forwardTo reply onCondition { true })
    edge(schedule forwardTo reply transformed { "이 일정을 보고 비서답게 알려 준다: $it" })
    edge(reply forwardTo nodeFinish transformed { it.textContent() })
}
