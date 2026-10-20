package com.example.phase3

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

@LLMDescription("캘린더")
class CalendarToolSet : ToolSet {
    val calls = mutableListOf<String>()
    private val base = listOf("10:00 팀 싱크", "14:00 디자인 리뷰")
    private val added = mutableMapOf<String, MutableList<String>>()

    @Tool
    @LLMDescription("날짜의 일정 목록을 돌려준다")
    fun schedules(@LLMDescription("날짜, 예: 내일") day: String): String {
        calls += day
        return (base + added[day].orEmpty()).sorted().joinToString(", ")
    }

    @Tool
    @LLMDescription("날짜에 일정을 추가한다")
    fun add(
        @LLMDescription("날짜, 예: 내일") day: String,
        @LLMDescription("시각, 예: 16:00") time: String,
        @LLMDescription("제목, 예: 치과") title: String,
    ): String {
        added.getOrPut(day) { mutableListOf() } += "$time $title"
        return "추가됨: $day $time $title"
    }
}
