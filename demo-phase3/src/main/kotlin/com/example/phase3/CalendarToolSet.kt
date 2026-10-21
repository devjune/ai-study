package com.example.phase3

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import java.nio.file.Files
import java.nio.file.Path

/** 발표용 캘린더. 일정은 텍스트 파일 하나에 저장해 재시작 뒤에도 남는다. 파일이 없으면 내일 두 건으로 시작한다. */
@LLMDescription("캘린더")
class CalendarToolSet(private val store: Path) : ToolSet {
    val calls = mutableListOf<String>()
    var lastAdded: Entry? = null
        private set
    private val entries: MutableMap<String, MutableList<String>> = load()

    @Tool
    @LLMDescription("날짜의 일정 목록을 돌려준다. 없으면 없음")
    fun schedules(@LLMDescription("날짜, 예: 내일") day: String): String {
        calls += day
        return entries[day]?.sorted()?.joinToString(", ") ?: "없음"
    }

    @Tool
    @LLMDescription("날짜에 일정을 추가한다")
    fun add(
        @LLMDescription("날짜, 예: 내일") day: String,
        @LLMDescription("시각, 예: 16:00") time: String,
        @LLMDescription("제목, 예: 치과") title: String,
    ): String {
        entries.getOrPut(day) { mutableListOf() } += "$time $title"
        save()
        lastAdded = Entry(day, time, title)
        return "추가됨: $day $time $title"
    }

    @Tool
    @LLMDescription("날짜의 일정을 지운다")
    fun remove(
        @LLMDescription("날짜, 예: 내일") day: String,
        @LLMDescription("시각, 예: 16:00") time: String,
        @LLMDescription("제목, 예: 치과") title: String,
    ): String {
        calls += "remove:$day $time $title"
        entries[day]?.remove("$time $title")
        save()
        return "삭제됨: $day $time $title"
    }

    /** 마지막으로 추가한 일정과 같은 시각에 다른 일정이 있으면 그 일정. 같은 일정이 이미 있었어도 겹침으로 본다. */
    fun conflictOfLastAdded(): String? {
        val added = lastAdded ?: return null
        val sameTime = entries[added.day].orEmpty().filter { it.startsWith(added.time) }
        if (sameTime.size < 2) return null
        return sameTime.firstOrNull { it != "${added.time} ${added.title}" } ?: sameTime.first()
    }

    // 한 줄에 "날짜<탭>시각 제목"
    private fun load(): MutableMap<String, MutableList<String>> {
        if (!Files.exists(store)) return mutableMapOf("내일" to mutableListOf("10:00 팀 싱크", "14:00 디자인 리뷰"))
        return Files.readAllLines(store).filter { it.isNotBlank() }
            .map { it.split('\t', limit = 2) }
            .groupBy({ it[0] }, { it[1] })
            .mapValues { it.value.toMutableList() }
            .toMutableMap()
    }

    private fun save() {
        store.parent?.let { Files.createDirectories(it) }
        Files.writeString(store, entries.flatMap { (day, list) -> list.map { "$day\t$it" } }.joinToString("\n"))
    }

    data class Entry(val day: String, val time: String, val title: String)
}
