package com.example.demo.compare

import com.example.demo.support.ModelInfo
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * 로컬 Ollama 에 실제로 설치된 모델 목록. 화면의 비교 대상이 여기서 나온다.
 *
 * Spring AI 는 모델을 "호출"하는 추상화라 "무엇이 설치돼 있나"는 다루지 않는다.
 * 그건 Ollama 고유 관리 API(`/api/tags`)의 영역이라 직접 부른다.
 */
@Component
class ModelCatalog(@Value("\${spring.ai.ollama.base-url}") baseUrl: String) {

    private val rest = RestClient.create(baseUrl)

    fun installed(): List<ModelInfo> =
        runCatching {
            rest.get().uri("/api/tags").retrieve().body(TagsResponse::class.java)
                ?.models
                ?.filterNot { it.name.startsWith(EMBEDDING_PREFIX) }
                ?.map { ModelInfo(it.name, "%.2f".format(it.size / GIB)) }
                ?.sortedBy { it.name }
                ?: emptyList()
        }.getOrElse { emptyList() }

    private companion object {
        const val GIB = 1024.0 * 1024 * 1024

        // 임베딩 전용 모델은 채팅 비교 대상이 아니다. RAG 축이 내부적으로만 쓴다.
        const val EMBEDDING_PREFIX = "nomic-embed"
    }
}

private data class TagsResponse(val models: List<TagEntry> = emptyList())

private data class TagEntry(val name: String = "", val size: Long = 0)
