package com.example.phase3

import ai.koog.prompt.executor.model.PromptExecutor
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ContextLoadsTest {
    @Autowired
    lateinit var promptExecutor: PromptExecutor

    @Test
    fun `Koog 스타터가 Spring AI ChatModel을 PromptExecutor로 감싼다`() {
        assertNotNull(promptExecutor)
    }
}
