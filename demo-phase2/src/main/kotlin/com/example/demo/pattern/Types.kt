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

data class Goal(val description: String, val successCriteria: String)

/** 패턴 하나를 실행하는 단위. */
interface PatternRunner {
    val spec: PatternSpec
    fun run(ctx: RunContext): RunResult
}
