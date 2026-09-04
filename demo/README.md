# 로컬 LLM 비교 데모 (phase2)

같은 요청을 여러 로컬 모델에 던져 **어디서 무너지는지** 관측하는 Spring Boot 앱.
**Spring Boot 4 + Spring AI 2.0 + 로컬 Ollama.**

**API 키가 필요 없다.** 상용 모델 의존이 없어 clone 후 바로 돌릴 수 있다.

학습 자료는 [docs/phase2](../docs/phase2), 사내망에서 돌리려면 [offline-kit.md](../docs/phase2/offline-kit.md).

## 요구사항

- JDK 21
- [Ollama](https://ollama.com) 및 모델 (아래)

## 준비

```bash
brew install ollama
ollama serve            # 별도 터미널. 또는: brew services start ollama

# 비교할 채팅 모델 (원하는 만큼)
ollama pull qwen3:1.7b      # 1.27 GiB — 작은 모델의 한계를 보는 용도
ollama pull qwen3:8b        # 4.87 GiB — 실용 하한선
ollama pull granite3.3      # 4.60 GiB — 같은 급 다른 계열
ollama pull glm4:9b         # 5.08 GiB — 도구 호출 미지원 대조군

# RAG 축이 쓰는 임베딩 (필수)
ollama pull nomic-embed-text
```

모델이 하나도 없어도 앱은 뜬다. 화면이 "설치된 모델 없음"을 안내한다.

## 실행

```bash
./gradlew bootRun
```

http://localhost:8080

## 무엇을 보는가

화면은 기능별 버튼이 아니라 **같은 요청 × 모델 N개 격자**다. 축을 고르고 질문을 던지면 선택한 모델들이 병렬로 실행되고, 카드가 끝나는 대로 채워진다.

| 시험 축 | 무엇을 재나 | 볼 것 |
| --- | --- | --- |
| 기본 대화 | 응답 품질·속도 | 대부분 통과한다. 크기에 따른 지연 차이를 여기서 체감해 둔다 |
| 구조화 출력 | `.entity(Movie)` 역직렬화 | 스키마를 벗어나면 **예외로 터진다**. 깨진 형태가 실패 카드에 그대로 나온다 |
| 도구 호출 | `@Tool` 실제 호출 여부 | 카드 아래 **도구 호출 횟수**. 0회인데 답이 그럴듯하면 지어낸 것이다 |
| RAG | 검색 문서 참조 여부 | **검색된 문서**와 답을 대조. 문서에 없는 숫자를 말하면 무시한 것이다 |

도구 호출과 RAG 축은 **응답 텍스트만으로는 성패를 알 수 없다.** 도구를 부르지 않고도 그럴듯한 배송 상태를 지어낼 수 있고, 검색 문서를 무시하고도 환불 규정처럼 들리는 답을 만들 수 있다. 그래서 카드마다 "모델이 실제로 무엇을 했는지"를 따로 표시한다.

## 코드 구조

```
com.example.demo
├── DemoApplication.kt
├── support/Dtos.kt          요청·결과 DTO
├── compare/
│   ├── CompareController.kt  GET /api/models · POST /api/run
│   ├── AxisRunner.kt         시험 축 4종 실행 + 모델 지정
│   └── ModelCatalog.kt       Ollama /api/tags 로 설치된 모델 조회
└── axis/
    ├── OrderTools.kt         도구 호출 축 (@Tool + 호출 이력)
    └── RagStore.kt           RAG 축 (지연 시드 VectorStore)
```

## 구현 메모

- **모델 전환** — 요청마다 포터블 `ChatOptions.builder().model(name)` 으로 지정한다. `ChatClient` 는 하나뿐이고 재시작이 필요 없다. 벤더 전용 `OllamaChatOptions` 대신 포터블 옵션을 쓴 것은 의도적이다. Ollama 를 OpenAI 호환 사내 서버로 바꿔도 이 코드는 그대로다 — 그게 이 데모의 논점이다.
  2.0 에서 `options()` 는 빌드된 인스턴스가 아니라 **빌더**를 받는다. 1.x 코드는 여기서 컴파일이 깨진다.

- **한 요청 = 한 모델** — 화면이 모델 수만큼 병렬로 던진다. 한 요청에 모든 모델을 처리하면 제일 느린 모델이 나머지를 붙잡는다.

- **실패는 예외가 아니라 결과다** — "이 모델은 이 축에서 실패한다"가 관측 대상이라, 예외를 잡아 `ok=false` 와 원문 메시지로 돌려준다. 구조화 출력이 어떤 형태로 깨졌는지가 학습 내용이므로 메시지를 감추지 않는다.

- **RAG 시드는 지연 초기화** — 빈 생성 시점에 문서를 임베딩하면 Ollama 가 떠 있어야만 앱이 뜬다. 그러면 RAG 하나 때문에 나머지 세 축도 못 본다. 첫 사용 시점으로 미뤄 두면 앱은 항상 뜨고 RAG 실패는 해당 카드에만 나타난다.

- **임베딩도 Ollama** — 채팅과 같은 채널이라 반입 경로가 하나로 통일된다. phase1 이 쓰던 ONNX 방식은 기본 URI 가 githubusercontent 라 사내망에서 깨졌다.

- **재시도 끔** (`spring.ai.retry.max-attempts=1`) — 기본값이 10회에 2초→10초→50초 백오프라, Ollama 가 없거나 모델이 없으면 화면이 몇 분씩 멈춘다. 비교 대상은 첫 시도의 거동이다.

- **온도 0.1 고정** — 실행마다 답이 흔들리면 무엇이 모델 차이고 무엇이 샘플링 노이즈인지 구분할 수 없다.

## 검증 상태

- `./gradlew build` 성공
- **Ollama 없이도 기동** 확인 — 화면·모델 목록·모든 축이 응답한다
- 실패 경로 확인 — Ollama 미기동 시 각 축이 약 2초 안에 구조화된 실패로 반환
- **모델을 실제로 붙인 검증은 아직이다.** 응답 품질, 구조화 출력 파싱 성공률, 도구 호출 여부, RAG 참조 여부는 모델을 내려받아 돌려봐야 안다. 결과는 [docs/phase2/study-log.md](../docs/phase2/study-log.md) 에 기록한다.
