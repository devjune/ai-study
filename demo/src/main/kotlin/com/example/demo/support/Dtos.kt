package com.example.demo.support

data class RunRequest(val axis: String, val model: String, val prompt: String)

/**
 * 한 모델의 한 축 실행 결과. 화면이 카드 하나로 그린다.
 *
 * [ok] 가 false 여도 응답이다 — "이 모델은 이 축에서 실패한다"가 관측 대상이라,
 * 실패를 예외로 흘리지 않고 결과로 돌려준다.
 */
data class RunResult(
    val model: String,
    val axis: String,
    val ok: Boolean,
    val elapsedMs: Long,
    val output: String?,
    val error: String?,
    val note: String?,
)

data class ModelInfo(val name: String, val sizeGiB: String)
