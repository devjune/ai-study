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

// Boot 3.5.14의 BOM은 kotlinx-serialization을 1.6.3으로 고정한다. Koog 1.3.0은
// 1.9 이상으로 컴파일돼 있어 그대로 두면 런타임에 메서드를 못 찾는다.
extra["kotlin-serialization.version"] = libs.versions.kotlinxSerialization.get()

dependencyManagement {
    imports {
        mavenBom(libs.spring.ai.bom.get().toString())
    }
}

dependencies {
    implementation(libs.spring.ai.starter.model.ollama)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.reflect)
    // Spring AI의 ChatModel을 Koog의 PromptExecutor로 감싸는 공식 브리지 스타터
    implementation(libs.koog.spring.ai.starter.model.chat)
    // AIAgent는 브리지 스타터가 아니라 이 아티팩트에 있다
    implementation(libs.koog.agents.jvm)
    implementation(libs.koog.skills.jvm)
    implementation(libs.koog.agents.ext.jvm)
    implementation(libs.jline)
    // Spring AI의 ChatMemoryRepository를 Koog의 ChatHistoryProvider로 감싸는 스타터
    implementation(libs.koog.spring.ai.starter.chat.memory)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.extensions.spring)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// 하네스 REPL이 터미널 입력을 받으려면 Gradle이 stdin을 넘겨줘야 한다
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    standardInput = System.`in`
}
