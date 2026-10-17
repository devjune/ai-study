package com.example.phase3

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.prompt.executor.model.PromptExecutor
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class JudgeLoopTest(val promptExecutor: PromptExecutor) : FunSpec({
    extensions(SpringExtension)

    test("심사가 승인할 때까지 초안을 고친다") {
        val judgeLoop = strategy<String, String>("judge-loop") {
            val draft by nodeLLMRequest()
            val judge by nodeLLMRequest()
            val emit by nodeLLMRequest()

            edge(nodeStart forwardTo draft)
            edge(draft forwardTo judge transformed {
                "방금 쓴 답장이 두 문장 이하의 존댓말이면 PASS, 아니면 FAIL 한 단어만 답한다."
            })
            edge(judge forwardTo emit onTextMessage { "PASS" in it.text } transformed {
                "승인된 답장을 그대로 다시 출력한다. 다른 말은 붙이지 않는다."
            })
            edge(judge forwardTo draft onTextMessage { true } transformed {
                "FAIL이다. 두 문장 이하의 존댓말로 답장을 다시 쓴다."
            })
            edge(emit forwardTo nodeFinish transformed { it.textContent() })
        }
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            strategy = judgeLoop,
            systemPrompt = "비서다. 요청받은 답장만 쓴다.",
            temperature = 0.2,
            maxIterations = 10,
        )
        val result = agent.run("내일 회식에 못 간다고 팀장님께 보낼 답장을 써 줘")
        println("JudgeLoopTest 응답: $result")
        result.shouldNotBeBlank()
        result shouldNotContain "PASS"
        result shouldNotContain "FAIL"
    }
})
