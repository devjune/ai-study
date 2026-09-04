package com.example.demo.axis

import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam

/**
 * 도구 호출 축에서 LLM 에게 쥐여주는 도구.
 *
 * 호출 이력을 남기는 것이 핵심이다. 약한 모델은 도구를 부르지 않고 그럴듯한
 * 답을 지어내는데, 응답 텍스트만 봐서는 구분되지 않는다. [calls] 가 비어 있으면
 * 모델이 도구를 무시했다는 뜻이다.
 *
 * 요청마다 새로 만들어 쓴다(공유하면 이력이 섞인다).
 */
class OrderTools {

    val calls = mutableListOf<String>()

    @Tool(description = "주문 번호로 주문 상태와 배송 정보를 조회한다")
    fun lookupOrder(@ToolParam(description = "주문 번호. 예: A-1024") orderId: String): String {
        calls += "lookupOrder(orderId=$orderId)"
        return ORDERS[orderId] ?: "주문번호 $orderId 를 찾을 수 없습니다."
    }

    @Tool(description = "고객센터 영업시간을 조회한다")
    fun businessHours(): String {
        calls += "businessHours()"
        return "평일 09:00~18:00, 주말·공휴일 휴무"
    }

    private companion object {
        val ORDERS = mapOf(
            "A-1024" to "주문 A-1024: 배송중. 2026-09-02 발송, 예상 도착 2026-09-05. 상품 무선 키보드 1개.",
            "A-2048" to "주문 A-2048: 결제완료. 아직 발송 전이며 2026-09-05 발송 예정. 상품 모니터 암 1개.",
        )
    }
}
