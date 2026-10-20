package com.example.phase3

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

object KoogModels {
    val QWEN3_4B_INSTRUCT = LLModel(
        provider = LLMProvider.Ollama,
        id = "qwen3:4b-instruct",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.Schema.JSON.Basic,
        ),
    )
}
