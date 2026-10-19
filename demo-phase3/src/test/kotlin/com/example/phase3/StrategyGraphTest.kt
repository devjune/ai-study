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

    test("두 노드를 잇는 커스텀 전략이 돈다") {
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
        println("StrategyGraphTest 체인 응답: $result")
        result shouldContain "15"
    }

    test("조건 엣지가 분기를 고른다") {
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            strategy = assistantRouter(),
            systemPrompt = "비서다. 짧게 답한다.",
            temperature = 0.2,
        )
        val result = agent.run("오늘 일정 알려줘")
        println("StrategyGraphTest 라우팅 응답: $result")
        result shouldBe "10:00 팀 싱크, 14:00 디자인 리뷰"
    }

    test("일반 질문은 모델이 받아 답한다") {
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            strategy = assistantRouter(),
            systemPrompt = "비서다. 짧게 답한다.",
            temperature = 0.2,
        )
        val result = agent.run("오늘 점심 뭐 먹을까")
        println("StrategyGraphTest 일반 응답: $result")
        result.shouldNotBeBlank()
        result shouldNotContain "팀 싱크"
    }

    test("전략에서 Mermaid 다이어그램이 나온다") {
        val diagram = assistantRouter().asMermaidDiagram()
        println("StrategyGraphTest 다이어그램:\n$diagram")
        diagram shouldContain "stateDiagram"
        diagram shouldContain "schedule"
        diagram shouldContain "schedule"
    }

    test("자기 자신으로 돌아오는 엣지가 루프가 된다") {
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
        agent.run("") shouldBe "..."
    }
})

private fun assistantRouter() = strategy<String, String>("route") {
    val schedule by node<String, String>("schedule") { CalendarToolSet().schedules(it) }
    val answer by nodeLLMRequest()

    edge(nodeStart forwardTo schedule onCondition { "일정" in it })
    edge(nodeStart forwardTo answer onCondition { true })
    edge(schedule forwardTo nodeFinish)
    edge(answer forwardTo nodeFinish transformed { it.textContent() })
}
