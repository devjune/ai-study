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
    // 관측성(9번 데모) — ObservationRegistry 를 만들어 준다.
    // 이게 없으면 Spring AI 는 NOOP registry 로 폴백해, log-prompt 를 켜도 아무것도 안 찍힌다.
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.ai.starter.model.anthropic)
    // RAG용 로컬 임베딩(ONNX) — 추가 API 키 없이 임베딩 생성
    implementation(libs.spring.ai.starter.model.transformers)
    // SimpleVectorStore(인메모리 벡터 저장소)
    implementation(libs.spring.ai.vector.store)
    // QuestionAnswerAdvisor (RAG)
    implementation(libs.spring.ai.vector.store.advisor)
    // ToolSearch — 대규모 도구에서 관련 도구만 선별 노출
    implementation(libs.spring.ai.tool.search.advisor)
    implementation(libs.spring.ai.tool.search.tool)
    // MCP 서버 (@McpTool) — MVC 기반
    implementation(libs.spring.ai.starter.mcp.server.webmvc)
    // 대화 메모리 영속화 — JDBC 저장소.
    // 운영에선 Postgres 등을 쓰지만, 데모는 설치 없이 돌리려고 H2 파일 모드를 쓴다.
    implementation(libs.spring.ai.starter.model.chat.memory.repository.jdbc)
    runtimeOnly(libs.h2)
    // H2 웹 콘솔 — 저장된 대화를 브라우저에서 확인. Boot 4 부터 별도 아티팩트다.
    // 데모 전용. 운영에선 절대 올리지 않는다(인증 없이 DB 가 열린다).
    runtimeOnly(libs.spring.boot.h2console)
    implementation(libs.kotlin.reflect)
}
