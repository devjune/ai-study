package com.example.demo.pattern

/** 모델이 채워 줄 그릇들. 이 클래스로 JSON 스키마가 만들어져 프롬프트에 실린다. */

data class Outline(val sections: List<String>)

data class Step(val order: Int, val instruction: String)
data class Plan(val steps: List<Step>)

data class RouteDecision(val route: String, val reason: String) {
    fun route(): Route =
        Route.entries.firstOrNull { it.name.equals(route.trim(), true) } ?: Route.GENERAL
}
enum class Route { ORDER, POLICY, GENERAL }

data class Task(val title: String, val priority: Int, val reason: String)
data class TaskList(val tasks: List<Task>)

data class Goal(val description: String, val successCriteria: String)

/** 패턴 하나를 실행하는 단위. */
interface PatternRunner {
    val spec: PatternSpec
    fun run(ctx: RunContext): RunResult
}

/** `RelevancyEvaluator` 는 feedback 이 비어 있을 때가 있다. 점수라도 보여준다. */
fun org.springframework.ai.evaluation.EvaluationResponse.verdict(): String {
    val head = if (isPass) "통과" else "미달"
    val detail = feedback?.takeIf { it.isNotBlank() }?.take(70) ?: "점수 %.2f".format(score)
    return "$head · $detail"
}
