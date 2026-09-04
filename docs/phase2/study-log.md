# phase2 학습 로그 — 로컬 LLM 연동

본문보다 날것의 메모. **관측한 것만** 적고, 예상은 예상이라고 표시한다.

---

## 실측 — Ollama 모델 크기와 도구 호출 지원 (2026-09-04)

`registry.ollama.ai`의 매니페스트를 직접 조회한 결과다. Ollama는 모델을 Docker Distribution v2 형식으로 서빙하므로 설치 없이도 크기를 알 수 있다.

```bash
curl -s https://registry.ollama.ai/v2/library/qwen3/manifests/8b
```

| 모델 | 크기 | 도구 호출 |
| --- | --- | --- |
| qwen3:0.6b | 0.49 GiB | — |
| qwen3:1.7b | 1.27 GiB | — |
| qwen3:4b | 2.33 GiB | 지원 |
| qwen3:8b | 4.87 GiB | 지원 |
| qwen3:14b | 8.64 GiB | — |
| glm4:9b | 5.08 GiB | 미지원 |
| gemma3:4b | 3.11 GiB | 미지원 |
| granite3.3 | 4.60 GiB | 지원 |

도구 호출 여부는 매니페스트의 template 레이어를 받아 도구 정의가 있는지로 판정했다. **추정이다** — `ollama show`로 확인해야 한다.

`tags/list` 엔드포인트는 404다. Ollama 레지스트리는 OCI 배포 API를 전부 구현하지는 않는다.

## 예상 (아직 검증 안 됨)

돌려보기 전의 가설이고, 결과가 나오면 이 절을 사실로 교체한다.

- 작은 모델일수록 구조화 출력에서 스키마를 벗어날 것이다
- 도구 호출은 "도구를 고르는 것"보다 "인자를 맞게 채우는 것"에서 먼저 깨질 것이다
- RAG는 준 컨텍스트를 무시하고 지어내는 쪽으로 실패할 것이다
- 기능이 요구하는 모델 지능은 기본대화 < RAG < 구조화 출력 < 도구 호출 < 다단계 에이전트 순일 것이다

## 사내망 반입 경로 타진 (2026-09-04)

시연 대상 노트북은 **git과 brew만** 열려 있다.

### GitHub으로 모델을 나를 수 있나 — 없다

- **GHCR을 Ollama 레지스트리로**: `registry.ollama.ai`가 Docker Distribution v2를 쓰고 GHCR 익명 토큰도 발급되지만, Ollama 클라이언트가 블롭 pull에서 `no Location header in response`로 실패한다. 레지스트리의 블롭 리다이렉트를 못 따라간다. 서드파티 OCI 레지스트리는 공식 미지원 (ollama/ollama#7244)
- **GitHub Releases**: 파일당 2 GiB 제한. qwen3:4b가 2.33 GiB로 이미 초과다. 총량·대역폭 제한이 없어 분할하면 우회되지만, 자산이 `release-assets.githubusercontent.com`으로 리다이렉트된다 — phase1에서 임베딩 ONNX가 막혔던 바로 그 호스트 계열이다
- **Git LFS**: 무료 한도 1GB 수준. 논외

```bash
curl -sIL https://github.com/ollama/ollama/releases/latest/download/ollama-darwin.tgz | grep -i location
# → https://release-assets.githubusercontent.com/...
```

### 그런데 brew가 열려 있다는 게 단서였다

Homebrew 병 저장소가 **ghcr.io**다.

```bash
curl -s https://formulae.brew.sh/api/formula/jq.json | jq -r .bottle.stable.root_url
# → https://ghcr.io/v2/homebrew/core
```

즉 brew가 되면 ghcr.io가 열려 있다. Ollama가 GHCR에서 직접 못 받는 것이지 GHCR 자체가 막힌 게 아니므로, **제대로 된 OCI 클라이언트를 중간에 끼우면 우회된다.** `ollama`와 `oras`가 둘 다 Homebrew 포뮬러로 있다.

절차는 [offline-kit.md](./offline-kit.md).

## 밟은 것

(작업하며 채운다)
