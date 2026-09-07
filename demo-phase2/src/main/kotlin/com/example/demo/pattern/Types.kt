package com.example.demo.pattern

/** 패턴 하나를 실행하는 단위. */
interface PatternRunner {
    val spec: PatternSpec
    fun run(ctx: RunContext): RunResult
}

// 아래는 모델이 채워 줄 그릇들. 이 클래스로 JSON 스키마가 만들어져 프롬프트에 실린다.
// 장 순서대로 둔다.

data class Outline(val sections: List<String>)

data class RouteDecision(val route: String, val reason: String) {
    /** 모델은 아무 문자열이나 넣을 수 있다. 못 알아본 값은 도구도 문서도 안 붙는 경로로 보낸다. */
    fun route(): Route =
        Route.entries.firstOrNull { it.name.equals(route.trim(), true) } ?: Route.GENERAL
}

enum class Route { ORDER, POLICY, GENERAL }

data class Plan(val steps: List<Step>)

data class Step(val order: Int, val instruction: String)

data class Goal(val description: String, val successCriteria: String)

data class Task(val title: String, val priority: Int, val reason: String)
