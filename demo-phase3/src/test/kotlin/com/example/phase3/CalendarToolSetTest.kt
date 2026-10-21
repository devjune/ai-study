package com.example.phase3

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class CalendarToolSetTest : FunSpec({

    test("추가한 일정은 파일에 남아 새 인스턴스에서도 보인다") {
        val store = Files.createTempDirectory("koog-calendar").resolve("calendar.txt")
        CalendarToolSet(store).add("내일", "15:00", "치과")

        CalendarToolSet(store).schedules("내일") shouldBe "10:00 팀 싱크, 14:00 디자인 리뷰, 15:00 치과"
    }

    test("같은 일정을 다시 추가하면 겹침으로 본다") {
        val store = Files.createTempDirectory("koog-calendar").resolve("calendar.txt")
        val first = CalendarToolSet(store)
        first.add("내일", "15:00", "치과")
        first.conflictOfLastAdded().shouldBeNull()

        val again = CalendarToolSet(store)
        again.add("내일", "15:00", "치과")
        again.conflictOfLastAdded() shouldBe "15:00 치과"
        again.remove("내일", "15:00", "치과")
        again.schedules("내일") shouldBe "10:00 팀 싱크, 14:00 디자인 리뷰, 15:00 치과"
    }
})
