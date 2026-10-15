package com.example.phase3

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.executor.model.PromptExecutor
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.or
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.contain
import org.springframework.boot.test.context.SpringBootTest
import java.net.HttpURLConnection
import java.net.URI

@SpringBootTest
class McpTest(val promptExecutor: PromptExecutor) : FunSpec({
    extensions(SpringExtension)

    val serverUp = phase2ServerUp()

    test("서버가 선언한 도구가 ToolRegistry로 들어온다").config(enabled = serverUp) {
        val registry = McpToolRegistryProvider.streamableHttp { url = PHASE2_MCP_ENDPOINT }

        registry.tools.forEach { println("McpTest 도구: ${it.descriptor.name} — ${it.descriptor.description}") }
        registry.tools.map { it.descriptor.name } shouldContainExactlyInAnyOrder
            listOf("lookupOrder", "businessHours")

        val lookup = registry.getTool("lookupOrder").descriptor
        lookup.description shouldBe "주문 번호로 주문 상태와 배송 정보를 조회한다"
        lookup.requiredParameters.map { it.name } shouldBe listOf("orderId")
        lookup.requiredParameters.single().description shouldBe "주문 번호. 예: A-1024"
        registry.getTool("businessHours").descriptor.requiredParameters.shouldBeEmpty()
    }

    test("에이전트가 MCP 도구를 골라 부른다").config(enabled = serverUp) {
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = KoogModels.QWEN3_4B_INSTRUCT,
            systemPrompt = "주문 문의에는 반드시 lookupOrder 도구를 호출한다. " +
                "도구가 돌려준 문장을 그대로 답에 싣고, 추측하지 않는다.",
            temperature = 0.0,
            toolRegistry = McpToolRegistryProvider.streamableHttp { url = PHASE2_MCP_ENDPOINT },
        )

        val result = agent.run("A-1024 주문 어떻게 됐어?")

        println("McpTest 에이전트 응답: $result")
        result should (contain("무선 키보드") or contain("배송중"))
    }
})

private const val PHASE2_MCP_ENDPOINT = "http://localhost:8080/mcp"

/**
 * demo-phase2가 MCP 서버로 떠 있어야 이 장이 돌아간다. 없으면 두 테스트를 건너뛴다.
 *
 *   cd demo-phase2 && ./gradlew bootRun
 */
private fun phase2ServerUp(): Boolean = runCatching {
    val connection = (URI(PHASE2_MCP_ENDPOINT).toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 500
        readTimeout = 2_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json, text/event-stream")
    }
    try {
        connection.outputStream.use {
            it.write("""{"jsonrpc":"2.0","id":0,"method":"tools/list","params":{}}""".toByteArray())
        }
        connection.responseCode == HttpURLConnection.HTTP_OK
    } finally {
        connection.disconnect()
    }
}.getOrDefault(false)
