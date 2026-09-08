package com.example.demo.pattern

import com.example.demo.support.OrderService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * 10·15장 — `@Tool` 이 함수를 모델에게 열어 준다면, `@McpTool` 은 **애플리케이션 밖으로** 연다.
 * 이 선언만으로 다른 언어로 짠 에이전트가 `tools/list` 로 스키마를 받아 간다.
 */
@Component
class McpTools(private val orderService: OrderService) {

    @McpTool(name = "lookupOrder", description = "주문 번호로 주문 상태와 배송 정보를 조회한다")
    fun lookupOrder(
        @McpToolParam(description = "주문 번호. 예: A-1024") orderId: String,
    ): String = orderService.find(orderId)?.summary() ?: "주문번호 $orderId 를 찾을 수 없습니다."

    @McpTool(name = "businessHours", description = "고객센터 영업시간을 조회한다")
    fun businessHours(): String = "평일 09:00~18:00, 주말·공휴일 휴무"
}

private val mcp = RestClient.create("http://localhost:8080")

/**
 * 자기 `/mcp` 엔드포인트를 실제로 부른다. 10·15장 러너가 이것을 써서,
 * 위의 선언이 프로토콜로 어떻게 나가는지 화면에 그대로 보여준다.
 */
fun mcpCall(method: String, params: String = "{}"): String =
    runCatching {
        mcp.post().uri("/mcp")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .body("""{"jsonrpc":"2.0","id":1,"method":"$method","params":$params}""")
            .retrieve().body(String::class.java).orEmpty()
    }.getOrElse { "호출 실패: ${it.message}" }
