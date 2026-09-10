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

    // LLMEmbeddingProvider.embed()에 넘긴 모델의 id가 Spring AI의 EmbeddingOptions.model이
    // 되므로, 이 id는 Ollama에 올라온 임베딩 모델 이름과 같아야 한다(7장).
    val BGE_M3 = LLModel(
        provider = LLMProvider.Ollama,
        id = "bge-m3",
        capabilities = listOf(LLMCapability.Embed),
    )
}
