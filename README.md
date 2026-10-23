# spring-ai-study

팀 내 AI 스터디 **학습 자료**. 대상은 Java/Spring 백엔드 개발자.

Spring AI를 주제로 단계(phase)를 나눠 진행한다. 각 phase는 학습 자료 문서와 **독립된 데모 프로젝트**를 갖는다. phase 사이에 코드를 공유하지 않으므로, 이전 phase의 데모는 그 시점 그대로 계속 돌아간다.

## 진행 상황

| Phase | 주제 | 상태 | 문서 | 데모 |
| --- | --- | --- | --- | --- |
| 1 | Spring AI 2.0 — 개념부터 에이전트까지 | 완료 | [docs/phase1](./docs/phase1) | [demo-phase1](./demo-phase1) |
| 2 | 에이전틱 패턴 (Spring AI 구현) | 완료 | [docs/phase2](./docs/phase2) | [demo-phase2](./demo-phase2) |
| 3 | Koog: Spring AI 위에 얹는 에이전트 프레임워크 | 완료 (2026-10 발표) | [docs/phase3](./docs/phase3) | [demo-phase3](./demo-phase3) |

### Phase 1 — Spring AI 2.0 개념부터 에이전트까지

Spring AI 2.0.1(2026-08) 기준으로 핵심 개념과 사용법을 처음부터 정리한다. 시작하기 → 핵심 기능 9개(ChatClient·Structured Output·ChatMemory·Tool Calling·RAG·ToolSearch·MCP·Advisor·관측성) → 직접 만들어 보기.

- [학습 자료 본문](./docs/phase1/Spring%20AI%202.0%20—%20개념부터%20에이전트까지.md) — 위키 포팅 대상
- [결정 로그](./docs/phase1/decisions.md) · [학습 로그](./docs/phase1/study-log.md)

### Phase 2 — 에이전틱 패턴

*Agentic Design Patterns*(Antonio Gulli, Springer 2025)가 정리한 **21개 패턴을 원서 목차 순서 그대로** Spring AI로 구현한다. 원서 예제는 전부 파이썬이고 Spring AI는 한 번도 나오지 않으므로, 대응 관계를 직접 확인해 대조표로 만들었다.

데모는 패턴마다 **모델을 몇 번 어떤 순서로 부르는지**를 타임라인으로 보여준다. `@Tool` 하나에 호출이 두 번인 것, 자기 검토가 검토 때문에 늘어나는 것이 눈으로 갈린다.

**상용 API 의존이 없다.** API 키 없이 clone → 실행하면 된다.

- [발표 자료 본문](./docs/phase2/Spring%20AI%EB%A1%9C%20%EA%B5%AC%ED%98%84%ED%95%98%EB%8A%94%20%EC%97%90%EC%9D%B4%EC%A0%84%ED%8B%B1%20%ED%8C%A8%ED%84%B4.md) — 위키 포팅 대상
- [결정 로그](./docs/phase2/decisions.md) · [학습 로그](./docs/phase2/study-log.md)
- [시연 메모](./docs/phase2/%EC%98%88%EC%A0%9C%20%EC%95%A0%ED%94%8C%EB%A6%AC%EC%BC%80%EC%9D%B4%EC%85%98%20%EC%8B%A4%ED%96%89%ED%95%98%EA%B8%B0.md) — 데모 실행법과 모델 선정 근거. 위키에는 올리지 않는다
- [오프라인 반입 키트](./docs/phase2/offline-kit.md) — 외부망이 막힌 사내망에서 돌리기
- [설계 문서](./docs/superpowers/specs/2026-09-04-phase2-local-llm-design.md)

### Phase 3 — Koog: Spring AI 위에 얹는 에이전트 프레임워크

JetBrains의 Kotlin 에이전트 프레임워크 [Koog](https://github.com/JetBrains/koog) 1.3.0을 공식 Spring AI 브리지로 기존 스택(Spring Boot 3.5, Spring AI 1.1.6, 로컬 Ollama) 위에 얹는다. Koog가 내세우는 그래프 워크플로, 퍼시스턴스, 관측을 여섯 단계(기본 에이전트, 도구, 스킬, 오케스트레이션, 챗 메모리, 퍼시스턴스)로 직접 돌려 보고, 끝에 Spring AI와 다른 점을 표로 정리한다. 퍼시스턴스는 강제 종료 뒤 재개와 겹치는 일정의 롤백(반대 도구 자동 호출)까지 실측했다.

- [발표 문서](./demo-phase3/docs/Koog.md) — 청중이 같이 읽는 본문. 코드와 출력은 데모와 같다. 위키 포팅 대상
- [발표 스크립트](./demo-phase3/docs/발표%20스크립트.md) · [데모 시나리오](./demo-phase3/docs/데모%20시나리오.md) — 할 말과 단계별 입력·실측 답
- [결정 로그](./docs/phase3/decisions.md) — 버전 고정, 체크포인트 키, 롤백 내부 동작처럼 코드로 확인한 것
- [실습: 망가뜨리며 배우기](./docs/phase3/실습%20—%20망가뜨리며%20배우기.md) · [Koog 읽기 가이드](./docs/phase3/koog-reading-guide.md)
- [본문 초안(9장)](./docs/phase3/Koog%20—%20Spring%20AI%20위에%20얹는%20에이전트%20프레임워크.md) — 2026-09 학습 자료 초안. 발표는 위 발표 문서로 갈음

## 데모

phase마다 **독립된 Gradle 프로젝트**다. 서로 의존하지 않으므로 각각 따로 열고 따로 실행한다.

| 프로젝트 | 벤더 | 실행 조건 |
| --- | --- | --- |
| [demo-phase1](./demo-phase1) | Anthropic (claude-sonnet-4-5) | `ANTHROPIC_API_KEY` 필요 |
| [demo-phase2](./demo-phase2) | 로컬 Ollama | **API 키 불필요.** Ollama와 모델만 있으면 된다. 21개 패턴을 실행해 볼 수 있다 |
| [demo-phase3](./demo-phase3) | 로컬 Ollama + Koog 1.3.0 | **API 키 불필요.** 터미널 REPL이라 포트를 안 쓴다. 비서 에이전트 하나로 여섯 단계를 돌린다 |

```bash
cd demo-phase2 && ./gradlew bootRun    # phase1과 둘 다 8080 포트라 동시에 띄우지 않는다
cd demo-phase3 && ./gradlew bootRun --args='--spring.profiles.active=harness'   # 터미널 REPL
```

## 관통하는 두 테마

- **이식성(Portability)** — 벤더나 DB를 바꿔도 코드는 유지된다. 2.0에서는 도구·에이전트 상호운용(MCP)으로까지 확장. phase2는 이 주장의 한계선을 잰다.
- **위임 vs 통제** — LLM에 흐름을 얼마나 맡기고, 어디에 가드레일을 칠 것인가. 모델이 약할수록 통제가 더 필요해진다.
