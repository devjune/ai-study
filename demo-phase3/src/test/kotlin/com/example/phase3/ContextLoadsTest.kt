package com.example.phase3

import ai.koog.prompt.executor.model.PromptExecutor
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ContextLoadsTest(@Autowired val promptExecutor: PromptExecutor) : FunSpec({
    extensions(SpringExtension)

    test("Koog 스타터가 Spring AI ChatModel을 PromptExecutor로 감싼다") {
        promptExecutor.shouldNotBeNull()
    }
})
