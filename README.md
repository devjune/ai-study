# spring-ai-study

팀 내 AI 스터디 **학습 자료**. 대상은 Java/Spring 백엔드 개발자.

Spring AI를 주제로 단계(phase)를 나눠 진행한다. 각 phase는 학습 자료 문서와 **독립된 데모 프로젝트**를 갖는다. phase 사이에 코드를 공유하지 않으므로, 이전 phase의 데모는 그 시점 그대로 계속 돌아간다.

## 진행 상황

| Phase | 주제 | 상태 | 문서 | 데모 |
| --- | --- | --- | --- | --- |
| 1 | Spring AI 2.0 — 개념부터 에이전트까지 | 완료 | [docs/phase1](./docs/phase1) | [demo-phase1](./demo-phase1) |
| 2 | 로컬 LLM 연동 | 진행 중 | [docs/phase2](./docs/phase2) | [demo-phase2](./demo-phase2) |
| 3 | MCP 상호운용 (외부 에이전트 결합) | 예정 | — | — |

### Phase 1 — Spring AI 2.0 개념부터 에이전트까지

Spring AI 2.0.0 GA(2026-06) 기준으로 핵심 개념과 사용법을 처음부터 정리한다. 시작하기 → 핵심 기능 9개(ChatClient·Structured Output·ChatMemory·Tool Calling·RAG·ToolSearch·MCP·Advisor·관측성) → 직접 만들어 보기.

- [학습 자료 본문](./docs/phase1/Spring%20AI%202.0%20—%20개념부터%20에이전트까지.md) — 위키 포팅 대상
- [결정 로그](./docs/phase1/decisions.md) · [학습 로그](./docs/phase1/study-log.md)

### Phase 2 — 로컬 LLM 연동

phase1이 설명만 하고 검증하지 않은 **이식성**을 실물로 시험한다. 상용 프런티어 모델에서 로컬 오픈 모델로 내려갔을 때 무엇이 그대로 돌고 무엇이 깨지는지를, 같은 요청을 여러 모델에 던져 나란히 놓고 관측한다.

**상용 API 의존이 없다.** API 키 없이 clone → 실행하면 된다.

- [결정 로그](./docs/phase2/decisions.md) · [학습 로그](./docs/phase2/study-log.md)
- [오프라인 반입 키트](./docs/phase2/offline-kit.md) — 외부망이 막힌 사내망에서 돌리기
- [설계 문서](./docs/superpowers/specs/2026-09-04-phase2-local-llm-design.md)

## 데모

phase마다 **독립된 Gradle 프로젝트**다. 서로 의존하지 않으므로 각각 따로 열고 따로 실행한다.

| 프로젝트 | 벤더 | 실행 조건 |
| --- | --- | --- |
| [demo-phase1](./demo-phase1) | Anthropic (claude-sonnet-4-5) | `ANTHROPIC_API_KEY` 필요 |
| [demo-phase2](./demo-phase2) | 로컬 Ollama | **API 키 불필요.** Ollama와 모델만 있으면 된다 |

```bash
cd demo-phase2 && ./gradlew bootRun    # 둘 다 8080 포트라 동시에 띄우지 않는다
```

## 관통하는 두 테마

- **이식성(Portability)** — 벤더나 DB를 바꿔도 코드는 유지된다. 2.0에서는 도구·에이전트 상호운용(MCP)으로까지 확장. phase2는 이 주장의 한계선을 잰다.
- **위임 vs 통제** — LLM에 흐름을 얼마나 맡기고, 어디에 가드레일을 칠 것인가. 모델이 약할수록 통제가 더 필요해진다.
