# Backlog — "나중" 권고 (지금 구현하지 않음)

검수(`playablecardgame-review.md`)에서 📌 "나중"으로 분류된 항목. 학습 스코프를 넘거나
현재 카드 풀에서 관측되는 차이가 없어 보류한다. 우선순위 순.

1. **존 변경 단일 진입점** `moveCard(card, from, to)` — 모든 존 이동/트리거 발화 지점 일원화. (zone)
2. **카드 데이터 외부화** — `CardDef`(불변 템플릿) ↔ `CardInstance`(가변) 분리 + sealed `Effect`에 `@Serializable`, `assets/cards.json` 로딩. (card-data)
3. **세이브/로드·리플레이** — kotlinx.serialization 전체 `GameState` 직렬화 → 액션 로그 event sourcing → undo/redo. RNG 시드를 상태에 포함해 결정론 확보. (state)
4. **효과 조합·타게팅 일반화** — `CompositeEffect(List<Effect>)`, `TargetSelector` 분리(효과×대상 폭발 방지). (effect)
5. **트리거 FIFO 디스패치 큐 + 루프 가드 + 큐 immutability** — 순환 트리거/동시 트리거가 생기는 카드를 추가할 때 필요. 현재 풀엔 미발생. (effect/timing)

## 명시적 스코프 밖 (구현하지 않음 — backlog 아님)
- 상호작용 스택/우선권/응답 대기창 (유희왕·MTG식) — 하스스톤 순차 모델이 의도적으로 생략.
- 지속형(Static/Aura)·대체(Replacement) 효과 — 연속 재계산 레이어 필요.
- 임베디드 스크립팅(Lua per-card).
- 도메인 전면 불변화(Redux 순수 reducer) — HANDOFF는 "가변 도메인 + UI만 불변" 명시.
- 멀티플레이·서버·과금·계정.
