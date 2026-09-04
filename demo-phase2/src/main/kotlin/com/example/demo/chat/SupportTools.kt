package com.example.demo.chat

import com.example.demo.support.OrderService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam

/**
 * 상담 봇이 쓰는 도구. 요청 하나에 하나씩 만들어 쓴다(호출 이력이 섞이지 않게).
 *
 * 조회 도구는 바로 실행하지만 **취소는 실행하지 않는다.** LLM 은 "취소하고 싶다"는
 * 의사만 남기고, 실제 변경은 사람이 승인 버튼을 눌러야 일어난다. 위험한 작업에서
 * 위임을 끊는 지점이다.
 */
class SupportTools(private val orders: OrderService) {

    val calls = mutableListOf<String>()

    /** 사람 승인을 기다리는 취소 요청. 비어 있으면 승인할 것이 없다. */
    var pendingCancel: String? = null
        private set

    @Tool(description = "주문 번호로 주문 상태와 배송 정보를 조회한다")
    fun lookupOrder(@ToolParam(description = "주문 번호. 예: A-1024") orderId: String): String {
        calls += "lookupOrder(orderId=$orderId)"
        return orders.find(orderId)?.summary() ?: "주문번호 $orderId 를 찾을 수 없습니다."
    }

    @Tool(description = "고객의 전체 주문 목록을 조회한다")
    fun listOrders(): String {
        calls += "listOrders()"
        return orders.all().joinToString("\n") { it.summary() }
    }

    @Tool(
        description = "주문 취소를 요청한다. 실제로 취소하지 않고 고객 승인을 받는 단계다. " +
            "취소 가능 여부를 확인해 돌려주므로, 결과를 고객에게 그대로 안내하라.",
    )
    fun requestCancellation(@ToolParam(description = "취소할 주문 번호") orderId: String): String {
        calls += "requestCancellation(orderId=$orderId)"
        orders.cancelBlockedReason(orderId)?.let { return "취소할 수 없습니다 — $it" }

        pendingCancel = orders.find(orderId)!!.id
        return "주문 $pendingCancel 은 취소 가능합니다. 고객이 승인 버튼을 눌러야 실제로 취소됩니다. " +
            "고객에게 승인을 요청하는 문장으로 답하라."
    }
}
