package com.example.demo.compare

import com.example.demo.support.ModelInfo
import com.example.demo.support.RunRequest
import com.example.demo.support.RunResult
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
 */
@RestController
@RequestMapping("/api")
class CompareController(
    private val catalog: ModelCatalog,
    private val runner: AxisRunner,
) {

    @GetMapping("/models")
    fun models(): List<ModelInfo> = catalog.installed()

    @PostMapping("/run")
    fun run(@RequestBody req: RunRequest): RunResult {
        val axis = Axis.entries.firstOrNull { it.name.equals(req.axis, ignoreCase = true) }
            ?: return RunResult(
                model = req.model,
                axis = req.axis,
                ok = false,
                elapsedMs = 0,
                output = null,
                error = "알 수 없는 시험 축: ${req.axis}",
                note = null,
            )
        return runner.run(axis, req.model, req.prompt)
    }
}
