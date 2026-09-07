package com.example.demo.pattern

/**
 * 모델 호출 한 번. 화면의 타임라인 막대 하나에 대응한다.
 *
 * 도구 호출처럼 `.call()` 한 번이 안에서 두 번 오가는 경우, 왕복마다 하나씩 쌓인다.
 * **막대 개수 = 모델이 실제로 불린 횟수**여야 패턴 간 비교가 정직해진다.
 */
data class ModelCall(
    val seq: Int,
    /** 어느 단계에서 나온 호출인가. "분류", "도구 지목" 같은 라벨 */
    val step: String,
    /** 그 호출에 실제로 나간 프롬프트 전문 */
    val prompt: String,
    val elapsedMs: Long,
)

/** 패턴 하나의 실행 결과. */
data class RunResult(
    val ok: Boolean,
    val output: String,
    val calls: List<ModelCall>,
    val elapsedMs: Long,
    /** 화면에 덧붙일 관측 — 검색 점수, 도구 호출 이력 등 */
    val notes: List<String> = emptyList(),
    /**
     * 13장 — 모델이 취소를 요청한 주문. 승인 버튼은 **이 값에만** 붙는다.
     * 화면이 취소 가능한 주문을 스스로 고르면, 아무도 요청하지 않은 주문을 사람이 승인하게 된다.
     */
    val pendingCancel: String? = null,
)

/** 패턴 메타데이터. 문서와 같은 내용을 화면에도 싣는다. */
data class PatternSpec(
    val chapter: Int,
    val name: String,
    val english: String,
    /** 문서의 "부품" 줄 */
    val parts: String,
    /** 이 패턴이 무엇을 푸는가 */
    val summary: String,
    /** 화면에 보여줄 코드. 추적용 래퍼는 뺀 알맹이 */
    val code: String,
    val defaultInput: String,
    /**
     * 이 장을 시연할 때 미리 골라 둘 모델.
     *
     * 패턴마다 전용 모델이 필요하다는 뜻이 아니다. 코드는 그대로고 모델만 갈아 끼우는데,
     * **어떤 장은 작은 모델로도 충분하고 어떤 장은 그렇지 않다**는 것 자체가 볼거리다.
     */
    val model: String = DEFAULT_MODEL,
    /** 이 장에 걸 토큰 상한. 긴 JSON 을 받는 장만 올린다 */
    val maxTokens: Int = RunContext.MAX_TOKENS,
) {
    companion object {
        const val DEFAULT_MODEL = "qwen3:4b-instruct"
    }
}
