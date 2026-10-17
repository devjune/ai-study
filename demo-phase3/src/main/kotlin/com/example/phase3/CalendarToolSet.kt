package com.example.phase3

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

@LLMDescription("캘린더 조회 도구")
class CalendarToolSet : ToolSet {
    val calls = mutableListOf<String>()

    @Tool
    @LLMDescription("날짜의 일정 목록을 돌려준다")
    fun schedules(@LLMDescription("날짜, 예: 내일") day: String): String {
        calls += day
        return "10:00 팀 싱크, 14:00 디자인 리뷰"
    }
}
