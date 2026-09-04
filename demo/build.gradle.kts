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
    // ObservationRegistry 를 만들어 준다. 없으면 Spring AI 가 NOOP 으로 폴백해
    // 토큰 사용량 지표가 조용히 비어 버린다.
    implementation(libs.spring.boot.starter.actuator)
    // 유일한 모델 벤더. 채팅과 임베딩을 모두 로컬 Ollama 에서 받는다.
    implementation(libs.spring.ai.starter.model.ollama)
    // SimpleVectorStore(인메모리) + QuestionAnswerAdvisor — RAG 시험 축
    implementation(libs.spring.ai.vector.store)
    implementation(libs.spring.ai.vector.store.advisor)
    implementation(libs.kotlin.reflect)
}
