# Koog 읽기 가이드

공식 문서를 스터디 흐름(기초→고급) 순서로 배열한 목록. 2026-09-11, docs.koog.ai 목차 기준.

읽는 규칙 하나 — 문서와 실물(1.2.0)의 간극이 있는 프레임워크다. 코드를 만나면 `demo-phase3`에서 돌려 본다. ⚠️ 표시는 이번 스터디에서 확인한 간극.

## 공식 채널

| 채널 | 주소 | 용도 |
|---|---|---|
| 문서 | https://docs.koog.ai | 본진 |
| API 레퍼런스 | https://api.koog.ai | 문서에 없는 시그니처는 여기부터 |
| Slack | https://docs.koog.ai/koog-slack-channel/ | kotlinlang 워크스페이스 #koog-agentic-framework |
| 이슈 트래커 | https://youtrack.jetbrains.com/issues/KG | Spring AI 2.x 지원 등 추적 |
| 저장소 | https://github.com/JetBrains/koog | 릴리스 노트 · examples 소스 |

## 필수 경로 — 발표 5부 순서

| 순서 | 페이지 | 대조할 우리 자료 |
|---|---|---|
| 1 | [Why Koog](https://docs.koog.ai/why-koog/) · [Key features](https://docs.koog.ai/key-features/) | 덱 0부 |
| 2 | [Quickstart](https://docs.koog.ai/quickstart/) → [Basic agents](https://docs.koog.ai/agents/basic-agents/) | 3장 · Ch03 |
| 3 | [Prompt executors](https://docs.koog.ai/prompts/prompt-executors/) · [Spring AI integration](https://docs.koog.ai/spring-ai-integration/) — **제약 절 필독** | 2·6·7장 |
| 4 | [Custom strategy graphs](https://docs.koog.ai/custom-strategy-graphs/) · [Pre-defined nodes](https://docs.koog.ai/nodes-and-components/) ⚠️ 예제가 그대로 컴파일되지 않음 | 4장 · Ch04 |
| 5 | [Annotation-based tools](https://docs.koog.ai/tools/annotation-based-tools/) | 5장 · Ch05 |
| 6 | [Chat memory](https://docs.koog.ai/features/chat-memory/) · [History compression](https://docs.koog.ai/history-compression/) | 6장 · Ch06 |
| 7 | [Embeddings](https://docs.koog.ai/embeddings/) · [RAG](https://docs.koog.ai/retrieval-augmented-generation/) | 7장 · Ch07 |
| 8 | [Model Context Protocol](https://docs.koog.ai/model-context-protocol/) ⚠️ streamableHttp 미기재 | 8장 · Ch08 |

## phase4 직결 — 9장의 "확인 안 함" 8패턴을 줄여줄 페이지

- **[Structured output](https://docs.koog.ai/structured-output/)** — `.entity()` 대체물. 미확인 4패턴(11·18·20·21)을 묶는 열쇠, 최우선
- [Parallel node execution](https://docs.koog.ai/parallel-node-execution/) — 앞 편 3장(병렬화)
- [Predefined strategies](https://docs.koog.ai/predefined-agent-strategies/) — 기본 전략의 정체
- [Handling failures](https://docs.koog.ai/prompts/handling-failures/) · [Agent persistence](https://docs.koog.ai/features/agent-persistence/) — 앞 편 12장(예외·복구)
- [Content moderation](https://docs.koog.ai/content-moderation/) — 앞 편 18장(가드레일)
- [A2A Protocol](https://docs.koog.ai/a2a/) — 앞 편 15장(에이전트 간 통신)

## 선택

- [Event handlers](https://docs.koog.ai/features/agent-event-handlers/) · [Events](https://docs.koog.ai/agent-events/) — 그래프 러너 구현이 쓸 페이지
- [Testing](https://docs.koog.ai/testing/) — Koog 자체 테스트 지원, 우리 Kotest 방식과 비교
- [OpenTelemetry](https://docs.koog.ai/features/open-telemetry/) · [Streaming API](https://docs.koog.ai/streaming-api/) · [Subgraphs](https://docs.koog.ai/subgraphs-overview/)
- 예제: [Calculator](https://docs.koog.ai/examples/Calculator/)(도구 기본) · [Banking](https://docs.koog.ai/examples/Banking/)(라우팅+도구 실전) · [Chess](https://docs.koog.ai/examples/Chess/)(전략 그래프 심화)
