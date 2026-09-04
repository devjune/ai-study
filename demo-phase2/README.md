# 주문 상담 봇 (phase2)

Spring AI 2.0 으로 만든 **주문 상담 챗봇**. 자연어로 말을 걸면 주문을 조회하고, 사내 규정에 근거해 답하고, 주문 취소를 **사람 승인을 받아** 처리한다.

**Spring Boot 4 + Spring AI 2.0 + 로컬 Ollama. API 키가 필요 없다.**

학습 자료는 [docs/phase2](../docs/phase2), 사내망에서 돌리려면 [offline-kit.md](../docs/phase2/offline-kit.md).

## 준비

```bash
brew install ollama
ollama serve            # 또는: brew services start ollama

ollama pull qwen3:1.7b      # 1.27 GiB — 이것만으로도 전부 동작한다
ollama pull bge-m3          # 1.2 GiB  — RAG 임베딩 (필수)

# 모델 비교 탭까지 보려면
ollama pull qwen3:8b        # 4.87 GiB
ollama pull granite3.3      # 4.60 GiB
ollama pull glm4:9b         # 5.08 GiB
```

```bash
./gradlew bootRun
```

http://localhost:8080

## 무엇을 보여주는가

### 상담 봇 (메인)

대화 한 번에 Spring AI 기능 대부분이 한 파이프라인에서 돈다.

```
"A-1024 지금 어디쯤 왔어?"          → @Tool 이 주문 조회
"단순 변심이면 배송비 얼마 나와?"    → RAG 가 사내 규정 인용
"그 주문 취소해줘"                  → 승인 요청 → [승인] 클릭 → 실제로 상태가 바뀐다
```

**조회만 하면 "검색이구나"로 끝난다.** 주문 취소처럼 실제로 데이터가 바뀌는 순간 위임의 무게가 달라지고, 그래서 가드레일이 왜 필요한지가 저절로 드러난다. LLM 은 `requestCancellation` 으로 **의사만 남기고**, 실제 변경은 승인 버튼이 실행한다.

### 실행 추적 패널

오른쪽에서 매 턴 무슨 일이 있었는지 펼쳐 본다.

- RAG 가 검색한 문서와 **유사도 점수**
- 모델이 요청한 도구와 인자
- **모델이 실제로 받은 프롬프트 전문**

프롬프트 원문을 보면 "마법"이 풀린다 — RAG 도 메모리도 결국 프롬프트 조립이다. 그리고 **그 원문을 꺼내는 수단이 다시 Spring AI 의 `BaseAdvisor`** 다. Advisor 를 배우려고 Advisor 를 쓰는 셈이다.

### 모델 비교 (탭)

같은 요청을 여러 모델에 던져 어디서 무너지는지 본다. 시험 축 5종 — 기본 대화 · 구조화 출력 · 도구 호출 · RAG · 라우팅.

상단에서 모델을 바꿔 끼우면 상담 봇도 그 모델로 돈다. **코드는 한 줄도 안 바뀐다.**

## 코드 구조

```
com.example.demo
├── DemoApplication.kt
├── chat/                     ← 상담 봇
│   ├── SupportController.kt   대화 + 승인 엔드포인트
│   ├── SupportTools.kt        @Tool — 조회는 실행, 취소는 승인 요청만
│   └── TraceAdvisor.kt        BaseAdvisor — 나간 프롬프트를 붙잡는다
├── compare/                  ← 모델 비교 탭
│   ├── CompareController.kt
│   ├── AxisRunner.kt          시험 축 5종
│   └── RoutingWorkflow.kt     분류(.entity) 후 경로 분기
├── axis/
│   ├── OrderTools.kt          비교 탭 도구 축
│   └── RagStore.kt            VectorStore (지연 시드)
└── support/
    ├── OrderService.kt        주문 저장소 — 상태가 실제로 바뀐다
    └── Dtos.kt
```

## 구현 메모

- **모델 전환** — 요청마다 포터블 `ChatOptions.builder().model(name)` 으로 지정한다. 벤더 전용 `OllamaChatOptions` 를 피한 것은 의도적이다. Ollama 를 OpenAI 호환 사내 서버로 바꿔도 코드가 그대로다.
  2.0 에서 `options()` 는 빌드된 인스턴스가 아니라 **빌더**를 받는다. 1.x 코드는 여기서 컴파일이 깨진다.

- **모델 접점은 전부 Spring AI 추상화다.** 설치된 모델을 알아내려고 Ollama 관리 API 를 직접 부르던 코드가 있었는데 걷어냈다. 비교 대상은 `demo.models` 프로퍼티로 정한다.

- **위험한 도구는 실행하지 않는다** — `requestCancellation` 은 취소 가능 여부만 확인하고 승인 대기 상태를 남긴다. `/api/support/approve` 가 실제 변경을 하고, **LLM 은 그 경로에 관여하지 않는다.**

- **RAG 검색 설정** — `similarityThreshold(0.5)` + `topK(3)`. 임계값이 핵심이다. 기본값은 무관한 문서도 topK 를 채워 무조건 주입하는데, 주문 상태를 묻는데 장비 대여 규정이 컨텍스트로 들어가면 모델이 그쪽에 끌려가 도구를 부르지 않는다. 실제로 겪었다.

- **임베딩은 `bge-m3`** — `nomic-embed-text` 로 시작했다가 한국어 변별력이 없어 교체했다. 점수가 0.65~0.82 로 평평하고 순위가 사실상 무작위였다. 자세한 진단 과정은 [study-log](../docs/phase2/study-log.md#임베딩을-갈아치우기까지--rag-가-조용히-틀리는-과정).

- **RAG 시드는 지연 초기화** — 빈 생성 시점에 임베딩하면 Ollama 없이는 앱이 기동조차 못 한다. `by lazy` 로 첫 사용까지 미룬다.

- **재시도 끔** (`spring.ai.retry.max-attempts=1`) — 기본값이 10회에 2초→10초→50초 백오프라, 모델이 없으면 화면이 몇 분씩 멈춘다.

- **온도 0.1 고정** — 모델 비교가 목적이라 실행마다 답이 흔들리면 무엇이 모델 차이고 무엇이 샘플링 노이즈인지 구분할 수 없다.

## 검증 상태

- `./gradlew build` 성공
- **Ollama 없이도 기동** — 화면이 뜨고 각 축이 약 2초 안에 구조화된 실패를 반환한다
- **상담 봇 시나리오 완주** — 조회 → 규정 안내 → 취소 요청 → 승인 → 주문 상태 변경 (qwen3:1.7b)
- **5개 축 × 4개 모델 실측** (M4 / RAM 32GB, 2026-09-04)

| 모델 | chat | structured | tool | RAG | 라우팅 정확도 |
| --- | --- | --- | --- | --- | --- |
| qwen3:1.7b | 4.2s | 4.8s | 호출 1회 7.1s | 6.8s | 6/6 |
| granite3.3 | 12.2s | 8.9s | 호출 1회 11.5s | 10.0s | 6/6 |
| qwen3:8b | 15.9s | 12.5s | 호출 1회 24.5s | 17.0s | 6/6 |
| glm4:9b | 6.0s | 9.4s | **미호출** 9.7s | 8.9s | 6/6 |

*소요시간은 임베딩 교체 전(nomic-embed-text) 측정치다.*

다섯 가지가 드러났다.

- **1.7B 모델도 5개 축을 전부 통과한다.** 구조화 출력이 한 번도 안 깨졌다. 이 규모·난이도에서는 모델 크기가 변별력이 없고 느려지기만 한다
- **glm4:9b 는 도구를 부르지 않지만 지어내지도 않는다** — 정직하게 거절한다. `ollama show` 에는 `tools` 지원으로 표기돼 있다. 표기는 "부를 수 있다"이지 "부른다"가 아니다
- **RAG 가 틀릴 때 범인은 LLM 이 아니었다.** 한국어 임베딩이 병목이었고, 유사도 점수를 찍어 보고서야 진단됐다. 모델 교체(설정 한 줄)로 정답 순위가 4위 → 1위가 됐다
- **워크플로 패턴은 공짜가 아니다.** 라우팅은 건너뛰는 경로가 분류 비용보다 비쌀 때만 이득이다. 이 규모에서는 오히려 느렸다
- **속도가 아니라 정확도 때문에 경로 분리가 필요했다.** 무관한 문서를 무조건 주입하면 도구 호출까지 망가진다
