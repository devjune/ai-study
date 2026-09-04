package com.example.demo.ch06_toolsearch

import com.example.demo.support.ChatRequest
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.ai.tool.toolsearch.index.regex.RegexToolIndex
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation

/** 여러 개의 도구. ToolSearch가 질문에 맞는 것만 선별해 LLM에 노출한다. */
class ManyTools {
    @Tool(description = "특정 도시의 현재 날씨를 조회한다")
    fun getWeather(@ToolParam(description = "도시 이름") city: String) = "$city: 맑음, 22도"

    @Tool(description = "두 수를 더한다")
    fun add(@ToolParam(description = "a") a: Int, @ToolParam(description = "b") b: Int) = a + b

    @Tool(description = "특정 사용자의 최근 주문 상태를 조회한다")
    fun getOrder(@ToolParam(description = "사용자 ID") userId: String) = "$userId 의 최근 주문: 배송중"

    @Tool(description = "현재 서버 시간을 반환한다")
    fun now() = LocalDateTime.now().toString()

    companion object {
        /** 후보 도구 목록. 하드코딩하지 않고 뽑아내, @Tool 을 추가해도 따로 손댈 곳이 없다. */
        val catalog: List<String> = ManyTools::class.declaredFunctions
            .mapNotNull { fn -> fn.findAnnotation<Tool>()?.let { "${fn.name} — ${it.description}" } }
            .sorted()
    }
}

/**
 * ToolSearch — 도구가 많을 때 질문에 관련된 것만 선별해 노출(progressive tool disclosure).
 * 문서 "핵심 기능 6. ToolSearch" 예제에 대응.
 *
 * 참고: 문서의 property 방식(spring.ai.chat.client.tool-search-advisor.enabled)은
 * 이 아티팩트들만으로는 autoconfigure가 없어, 여기서는 advisor를 수동 구성한다.
 * regex 인덱스 사용.
 */
@RestController
@RequestMapping("/api/toolsearch")
class ToolSearchController(builder: ChatClient.Builder) {

    private val chatClient = builder.build()

    private val toolSearchAdvisor = ToolSearchToolCallingAdvisor.builder()
        .toolIndex(RegexToolIndex())
        .toolCallingManager(ToolCallingManager.builder().build())
        .build()

    @PostMapping("/chat")
    fun chat(@RequestBody req: ChatRequest): ToolSearchReply =
        ToolSearchReply(
            availableTools = ManyTools.catalog,
            reply = chatClient.prompt()
                .advisors(toolSearchAdvisor)
                // ToolSearch는 세션별 도구 인덱스를 유지하므로 세션 ID가 필요하다.
                .advisors { it.param(ChatMemory.CONVERSATION_ID, "toolsearch-demo") }
                .tools(ManyTools())
                .user(req.message)
                .call()
                .content(),
        )
}

/** 후보 도구를 함께 돌려줘, ToolSearch 가 무엇 중에서 골랐는지 보여준다. */
data class ToolSearchReply(
    val availableTools: List<String>,
    val reply: String?,
)
