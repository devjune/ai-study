package com.example.demo.support

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 주문 저장소. 실제로 **상태가 바뀐다**.
 *
 * 조회만 하는 도구는 "검색이구나"로 끝나지만, LLM 의 판단이 실제 데이터를 바꾸는 순간
 * 위임의 무게가 달라진다. 이 데모가 사람 승인을 끼우는 이유다.
 */
@Service
class OrderService {

    private val orders = ConcurrentHashMap(SEED.associateBy { it.id })

    private companion object {
        val SEED = listOf(
            Order("A-1024", "무선 키보드", "배송중", "2026-09-02 발송 · 09-05 도착 예정"),
            Order("A-2048", "모니터 암", "결제완료", "09-05 발송 예정"),
            Order("A-3072", "USB-C 허브", "배송완료", "08-28 수령"),
        )
    }

    fun all(): List<Order> = orders.values.sortedBy { it.id }

    /** 시연을 반복하려면 상태를 되돌려야 한다. */
    fun reset(): List<Order> {
        orders.clear()
        orders.putAll(SEED.associateBy { it.id })
        return all()
    }

    fun find(orderId: String): Order? = orders[orderId.trim().uppercase()]

    /** 취소 가능 여부. 실패 사유를 문자열로 돌려준다(성공이면 null). */
    fun cancelBlockedReason(orderId: String): String? {
        val order = find(orderId) ?: return "주문번호 $orderId 를 찾을 수 없습니다."
        return when (order.status) {
            "취소됨" -> "이미 취소된 주문입니다."
            "배송완료" -> "배송이 완료되어 취소할 수 없습니다. 반품으로 진행해야 합니다."
            else -> null
        }
    }

    /** 실제 취소. **사람 승인을 거친 뒤에만** 호출된다. */
    fun cancel(orderId: String): Order? {
        val key = orderId.trim().uppercase()
        val order = orders[key] ?: return null
        val cancelled = order.copy(status = "취소됨", detail = "고객 요청으로 취소 · 환불 3~5영업일")
        orders[key] = cancelled
        return cancelled
    }
}

data class Order(val id: String, val product: String, val status: String, val detail: String) {
    fun summary() = "$id · $product · $status · $detail"
}
