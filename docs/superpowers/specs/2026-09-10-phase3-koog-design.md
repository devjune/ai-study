# phase3 설계 — Koog, Spring AI 위에 얹는 에이전트 프레임워크

작성일: 2026-09-10
상태: 초안 (설계 방향 승인됨)

## 배경

phase2(`Spring AI로 구현하는 에이전틱 패턴`)는 21개 에이전틱 패턴을 ChatClient · `@Tool` · `.entity()` 같은 원시 재료로 **직접 조립**했다. 전용 타입이 있는 장은 다섯뿐이고, 나머지 열여섯은 범용 기능의 조합이었다.

phase3는 그 반대편을 본다. **전용 에이전트 프레임워크는 이걸 어떻게 제공하는가.** 대상은 JetBrains의 [Koog](https://github.com/JetBrains/koog)다.

phase2 스펙은 phase3 후보로 "MCP 상호운용 및 LangChain/LangGraph 결합"을 적어 두었다. Koog로 바꾼 이유: Kotlin 네이티브라 기존 코드베이스와 언어가 같고, `koog-spring-ai` 공식 브리지가 있어 phase2의 Spring AI · Ollama 기반을 그대로 재사용한다. LangGraph는 파이썬 생태계라 스터디의 "같은 코드베이스에서 이어 간다" 흐름이 끊긴다.

## 왜 이 주제인가

phase2에서 손으로 만든 Reflection · Planning · Multi-Agent가 프레임워크에선 몇 줄이 되는지, 그리고 **프레임워크가 공짜로 주는 것**(그래프 전략, 재시도·상태 복원, 히스토리 압축, 관측성)과 **가려서 잃는 것**(브리지 어댑터의 제약)을 실물로 확인한다.

학습 목표는 phase2와 대칭이다 — 손조립의 경계를 봤으니, 이번엔 **프레임워크 추상화의 경계**를 본다.

## 결정 사항

1. **각도** — Spring AI 브리지 중심. 기존 Spring AI 빈(ChatModel · EmbeddingModel · ChatMemoryRepository · VectorStore)을 `koog-spring-ai` 어댑터로 Koog에 연결한다. Koog 자체 Ollama 클라이언트는 비교 언급만.
2. **산출물** — 문서 먼저. `docs/phase3/` 아래 한 편. 데모 웹 앱은 이후 별도 판단.
3. **목차 축** — Koog 기능 순서. phase2 패턴 대응은 마지막 장에서 표로 회수.
4. **코드 검증** — 문서의 모든 코드 블록은 실제 빌드·실행으로 검증한다. 검증 프로젝트는 처음부터 `demo-phase3` 모듈로 만든다(UI 없음, 장별 실행 예제만). 나중에 데모 앱의 씨앗이 된다.

## 목차 초안

| 장 | 내용 |
|---|---|
| 1 | Koog는 무엇이고 왜 — phase2의 손조립과 무엇이 다른가 |
| 2 | 연결 — `koog-spring-ai-starter-model-chat`: 기존 Ollama `ChatModel` 빈이 `PromptExecutor`가 되는 구조 |
| 3 | 단일 에이전트 — `AIAgent` 기본형 |
| 4 | 그래프 전략 — 노드·엣지로 흐름 짜기. phase2의 체이닝·라우팅·리플렉션이 여기서 재등장 |
| 5 | 도구 — Koog 도구 정의. 실행은 Koog가 하고 Spring AI는 스키마만 받는 구조 |
| 6 | 메모리 — `ChatMemoryRepository` 어댑터와 제약(도구 호출·추론 메시지는 저장되지 않음), 히스토리 압축 |
| 7 | RAG — `EmbeddingModel` · `VectorStore` 어댑터 |
| 8 | MCP 연동 |
| 9 | 마무리 — phase2 패턴 대응표: 손으로 만든 것이 Koog에선 무엇이 되나 |

## 기술 전제

- ~~Spring Boot 4.0.0 · Spring AI 2.0.1~~ → **Spring Boot 3.5.14 · Spring AI 1.1.6 · Koog 1.2.0** (본체는 정식, Spring AI 브리지·MCP 모듈만 `1.2.0-beta` 트랙) (2026-09-10 스파이크 수정 — 아래 참조)
- 모델은 phase2와 동일: Ollama의 qwen3:4b-instruct · granite4:micro · bge-m3. API 키 불필요 원칙 유지
- `demo-phase3`는 `demo-phase2`와 별개의 Gradle 프로젝트

**2026-09-10 스파이크 수정**: `koog-spring-ai` 1.2.0-beta는 Spring AI 1.1.6 · Boot 3.5.14를 타깃하고, Spring AI 2.0.1에서는 런타임에 깨진다(`DefaultChatOptions` → `OllamaChatOptions` ClassCastException). Koog의 어떤 릴리스도 Spring AI 2.x를 타깃한 적이 없다. demo-phase3만 브리지의 타깃 버전으로 내리고, 이 간극 자체를 문서 2장의 1급 관찰로 싣는다. 상세는 `docs/phase3/decisions.md`.

## 리스크 — Kotlin 버전

이 저장소는 Kotlin을 **2.2.21로 고정**했다(Spring Boot 4.0.0 BOM의 kotlin.version과 일치, 올리면 stdlib이 어긋난다는 주석 있음). Koog 1.2.0은 **Kotlin 2.3.10+**를 명시한다.

Kotlin 컴파일러는 한 마이너 앞 메타데이터까지 읽으므로 2.2 컨슈머로 동작할 가능성이 있지만, 보장은 아니다. 따라서 **첫 작업은 호환 스파이크**다: `demo-phase3`에서 Koog 의존성을 넣고 최소 `AIAgent` 하나를 Ollama로 실행해 본다.

- 통과 → 그대로 진행
- 실패 → `demo-phase3`만 Kotlin 2.3.x로 올리는 방안 검토(별개 Gradle 프로젝트라 격리 가능). 그래도 안 되면 Koog 버전을 내리거나 주제 재검토

## 작업 순서

1. 호환 스파이크 — `demo-phase3` 골격 + Koog 의존성 + 최소 에이전트 실행
2. 장 순서대로: 예제를 검증 프로젝트에서 돌려 본 뒤 해당 장을 집필
3. 9장 대응표는 1~8장이 끝난 뒤 작성

## 범위

**포함**: Koog 핵심 기능, `koog-spring-ai` 브리지 구조와 제약, phase2 패턴 대응
**제외**: 데모 웹 앱(이후 판단), Koog 자체 클라이언트 심화, 멀티플랫폼(Android · iOS · JS), ACP
