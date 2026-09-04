package com.example.demo.ch09_mcp

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

/**
 * MCP 서버로 노출되는 도구. @McpTool 선언만으로 이 앱이 MCP 서버가 되어,
 * 다른 에이전트(MCP 클라이언트)가 표준 프로토콜로 이 도구를 호출할 수 있다.
 * 문서 "핵심 기능 7. MCP" 예제에 대응.
 *
 * 확인: 앱 기동 후 MCP 엔드포인트로 tools/list 하면 add, echo 가 노출된다.
 */
@Component
class McpCalculatorTools {

    @McpTool(name = "add", description = "두 수를 더한다")
    fun add(
        @McpToolParam(description = "첫 번째 수") a: Int,
        @McpToolParam(description = "두 번째 수") b: Int,
    ): Int = a + b

    @McpTool(name = "echo", description = "입력을 그대로 돌려준다")
    fun echo(@McpToolParam(description = "메시지") message: String): String = message
}
