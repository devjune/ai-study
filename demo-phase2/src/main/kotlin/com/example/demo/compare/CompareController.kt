package com.example.demo.compare

import com.example.demo.support.RunRequest
import com.example.demo.support.RunResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 모델 비교 API.
 *
 * 한 요청이 한 모델만 실행한다. 화면이 모델 수만큼 병렬로 던지므로, 느린 모델이
 * 빠른 모델의 결과를 붙잡아 두지 않고 카드가 끝나는 대로 채워진다.
 *
 * 비교 대상은 `demo.models` 프로퍼티로 정한다. Ollama 관리 API 를 직접 부르면
 * 설치된 모델을 자동으로 알아낼 수 있지만, 그 순간 Spring AI 추상화 밖으로 나가
 * 벤더를 바꿀 때 이 부분만 깨진다.
 */
@RestController
@RequestMapping("/api")
class CompareController(
    @Value("\${demo.models}") private val models: List<String>,
    private val runner: AxisRunner,
) {

    @GetMapping("/models")
    fun models(): List<String> = models

    @PostMapping("/run")
    fun run(@RequestBody req: RunRequest): RunResult {
        val axis = Axis.entries.firstOrNull { it.name.equals(req.axis, ignoreCase = true) }
            ?: return RunResult(req.model, false, 0, "알 수 없는 시험 축: ${req.axis}")
        return runner.run(axis, req.model, req.prompt)
    }
}
