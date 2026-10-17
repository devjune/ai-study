package com.example.phase3

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.runBlocking
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 미니 하네스 — 입력 루프 하나와 에이전트 하나가 전부다.
 *
 *   ./gradlew bootRun --args='--spring.profiles.active=harness --spring.main.web-application-type=none'
 */
@Component
@Profile("harness")
class Harness(private val promptExecutor: PromptExecutor) : CommandLineRunner {

    override fun run(vararg args: String?) = runBlocking {
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            systemPrompt = "짧게 답한다.",
        )
        while (true) {
            print("❯ ")
            val line = readLine() ?: break
            if (line.isBlank()) continue
            if (line == "exit") break
            println(agent.run(line))
        }
    }
}
