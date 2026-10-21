package com.example.phase3

import java.nio.file.Files

/** 테스트마다 새 임시 파일 경로를 쓰는 캘린더. 파일이 없으니 기본 일정(내일 두 건)으로 시작한다. */
fun tempCalendar() = CalendarToolSet(Files.createTempDirectory("koog-calendar").resolve("calendar.txt"))
