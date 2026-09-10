plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.ai.bom.get().toString())
    }
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.ai.starter.model.ollama)
    // SimpleVectorStore(인메모리) — 7장
    implementation(libs.spring.ai.vector.store)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.reflect)
    // Spring AI의 ChatModel을 Koog의 PromptExecutor로 감싸는 공식 브리지 스타터
    implementation(libs.koog.spring.ai.starter.model.chat)
    // AIAgent는 브리지 스타터가 아니라 이 아티팩트에 있다
    implementation(libs.koog.agents.jvm)
    // Spring AI의 ChatMemoryRepository를 Koog의 ChatHistoryProvider로 감싸는 스타터
    implementation(libs.koog.spring.ai.starter.chat.memory)
    // Spring AI의 EmbeddingModel을 Koog의 LLMEmbeddingProvider로 감싸는 스타터
    implementation(libs.koog.spring.ai.starter.model.embedding)
    // Spring AI의 VectorStore를 Koog의 KoogVectorStore로 감싸는 스타터
    implementation(libs.koog.spring.ai.starter.vector.store)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.extensions.spring)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
