package com.example.demo.ch04_tool

import com.example.demo.support.ChatReply
import com.example.demo.support.ChatRequest
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** LLM이 호출할 도구. @Tool description 이 "언제 쓸지" 힌트. */
class WeatherTools {

    @Tool(description = "특정 도시의 현재 날씨를 조회한다")
    fun getWeather(@ToolParam(description = "도시 이름") city: String): String {
        // 실제로는 외부 API/DB 조회. 데모라 고정값.
        return "$city 의 현재 날씨: 맑음, 22도"
    }
}

/**
 * Tool Calling — LLM이 @Tool 함수를 필요할 때 호출.
 * 문서 "핵심 기능 4. Tool Calling" 예제에 대응.
 */
@RestController
@RequestMapping("/api/tool")
class ToolController(builder: ChatClient.Builder) {

    private val chatClient = builder.build()

    @PostMapping("/chat")
    fun chat(@RequestBody req: ChatRequest): ChatReply =
        ChatReply(
            chatClient.prompt()
                .user(req.message)
                .tools(WeatherTools())
                .call()
                .content(),
        )
}
