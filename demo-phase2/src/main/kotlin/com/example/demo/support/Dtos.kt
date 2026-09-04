package com.example.demo.support

data class RunRequest(val axis: String, val model: String, val prompt: String)

/**
 * 한 모델의 한 축 실행 결과.
 *
 * 실패해도 예외로 흘리지 않고 [ok]=false 로 돌려준다 — "이 모델은 이 축에서
 * 실패한다"가 곧 관측 대상이라, 실패는 크래시가 아니라 결과여야 한다.
 */
data class RunResult(
    val model: String,
    val ok: Boolean,
    val elapsedMs: Long,
    /** 성공이면 응답, 실패면 오류 메시지. */
    val text: String,
    /** 모델이 실제로 무엇을 했는지 — 도구 호출 횟수, 검색된 문서. */
    val note: String? = null,
)
