# PlayableCardGame 통합 검수 보고서 (Phase 2)

> 주 에이전트가 5개 리서처 보고서(`research/*.md`)를 `PlayableCardGame.kt`(기준 구현)와 대조해 작성한 갭 분석.
> 분류: ✅ 잘 맞음(유지) · ⚠️ 부족/개선후보 · 🟦 의도적 단순화(스코프 밖, 추가 금지) · 📌 권고(지금/나중).
>
> **전제**: 이 게임은 "의도적으로 단순한 하스스톤 모델"(상호작용 스택/체인 없음, 순차 해결). 검수 목표는 최대주의가 아니라 **적합성(fit)**.
> **원본 부재 주석**: 리서치 시점에 `PlayableCardGame.kt`가 저장소에 없어, HANDOFF 부록 A의 도메인 요약을 기준으로 재구성한 콘솔 버전을 기준 구현으로 삼았다.

---

## 1. 존(Zone) 시스템 — `research/zone-system.md`

| 분류 | 내용 |
|---|---|
| ✅ 유지 | 덱/손패/전장/묘지 4존을 `List` 기반 `Zone`으로. Forge `ZoneType`+컬렉션 패턴과 동일. 덱 = 정렬 리스트(top=index 0 규약), `shuffle()` 한 줄. |
| ✅ 유지 | 용량 규칙: 전장 7(초과 소환 거부), 손패 10(초과분 "burn"=묘지), 빈 덱 드로우→탈진(누적 1,2,3…). 모두 기준 구현에 1:1 반영됨. |
| ⚠️→지금 | **트리거가 순회 중인 존을 변경할 때 `ConcurrentModificationException`**. → 효과 해결·죽음 처리에서 `zone.cards.toList()`로 순회하도록 적용 **(반영 완료)**. |
| ⚠️→지금 | **동시 사망 순서**: "죽은 카드 전부 제거 후, 죽음의 메아리를 play order로 해결". → `processDeaths()`에서 dead 일괄 제거 후 트리거 발행하는 안정화 루프로 **반영 완료**. |
| 🟦 스코프 밖 | 제외(exile)/비밀(secret) 존, 유희왕식 고정 슬롯, owner vs controller 분리 — 2인·통제권 탈취 없음 → 도입 안 함. |
| 📌 나중 | 단일 `moveCard(card, from, to)` 진입점으로 모든 존 변경 통일(트리거 발화 지점 일원화). 현재는 카드 종류가 적어 인라인으로 충분 → backlog. |

## 2. 카드 = 데이터 + 효과 스크립트 — `research/card-data-effect-script.md`

| 분류 | 내용 |
|---|---|
| ✅ 유지 | `sealed Effect` + `Target`/`Trigger` enum + `Ability(trigger,effect)` 는 업계 표준 "파라미터화된 효과 어휘"(MTG Forge DSL, Unity TCG Engine ScriptableObject)와 구조 동일. 적정 표현력. |
| ⚠️→지금 | **안정 문자열 id**(인덱스/이름 의존 금지). → `Card.defId`("pyromancer" 등) + 인스턴스 단위 `CardId` 추가 **반영 완료**. |
| 🟦 스코프 밖 | 임베디드 스크립팅(Lua per-card, YGOPro `cards.cdb`). sealed 클래스 추가로 충분 → 과잉. |
| 📌 나중 | `CardDef`(불변 템플릿) ↔ `CardInstance`(가변 사본) 완전 분리, sealed 계층에 `@Serializable`+`assets/cards.json` 외부화. 학습 스코프엔 과함 → backlog(직렬화와 함께). |

## 3. 효과/능력 시스템 — `research/effect-ability-system.md`

| 분류 | 내용 |
|---|---|
| ✅ 유지 | `EventBus`(Observer, "언제") + `Effect`(Command, "무엇을") 분리 = SabberStone/Fireplace의 핵심 철학. `when(cardName)` 하드코딩 회피. 동일 효과(드로우)를 함성/메아리가 공유. |
| ✅ 유지 | 트리거 처리 2단계(스냅샷→해결). 죽음의 메아리는 사망 일괄 처리 후 발행 → 반영됨. |
| 🟦 스코프 밖 | **지속형(Static/Aura)·대체(Replacement) 효과** — 연속 재계산 "onion" 레이어 필요, 이벤트로 안 됨. 학습 프로토타입 범위 밖. |
| 📌 나중 | `CompositeEffect(List<Effect>)`(콤보), `TargetSelector` 분리(효과×대상 폭발을 곱셈→덧셈). 현재 카드엔 불필요 → backlog. |
| 📌 나중 | 무한 트리거 루프 방어용 FIFO 디스패치 큐 + 루프 가드. 현재 카드 풀엔 순환 트리거가 없어 미발생 → backlog로 기록. |

## 4. 타이밍·우선권 — `research/timing-priority.md`

| 분류 | 내용 |
|---|---|
| ✅ 유지 | **하스스톤 순차 큐 모델 채택이 정답.** 현재 플레이어 액션이 즉시·완전 해결, 상대 인터럽트 없음. |
| 🟦 스코프 밖 (핵심) | 상호작용 스택, 우선권/패스, 응답 대기창, instant/counter 속도, LIFO, APNAP/SEGOC/Spell Speed — 하스스톤이 **의도적으로 생략**한 것 전부. MTG/유희왕은 "비교 대상"일 뿐 도입 권고 아님 → **추가 금지**. |
| ✅ 유지 | 공격/반격 동시 피해 후 죽음 일괄 처리, 승패 체크 위치. 반영됨. |
| 📌 나중 | 트리거 큐 immutability(해결 시작 시 스냅샷, 도중 생성 트리거는 다음 큐로 연기). 현재 효과가 단순해 관측되는 차이 없음 → backlog. |

## 5. 상태 관리·직렬화 — `research/state-serialization.md`

| 분류 | 내용 |
|---|---|
| ✅ 유지 | 가변 도메인(단일 진실 저장소) + ViewModel이 매 액션 후 **불변 `GameUiState`로 copy해 `StateFlow` emit**. HANDOFF "가변→불변 스냅샷"과 직결. |
| ⚠️→지금 | **참조 동일성**: 같은 인스턴스 emit 시 Compose 리컴포즈 안 함 / StateFlow가 equals로 중복 제거. → 매번 새 `GameUiState`/리스트 생성으로 **반영(ViewModel 단계에서)**. |
| ⚠️→지금 | **앨리어싱 누수**: 내부 가변 컬렉션을 스냅샷에 그대로 넘기면 이후 변경이 과거 스냅샷을 오염. → UI 모델은 도메인 `Card`를 직접 노출하지 않고 값만 복사 **(MinionUi/CardUi)**. |
| 🟦 스코프 밖 | Redux식 순수 reducer(불변 `GameState.copy`)로 도메인 전면 전환 — HANDOFF는 "가변 도메인 유지 + UI만 불변"을 명시. 전면 불변화는 비용 대비 이득 적음 → 도입 안 함. |
| 📌 나중 | kotlinx.serialization 으로 전체 `GameState` 세이브/로드 → 액션 로그 리플레이(event sourcing) → undo/redo, RNG 시드 상태화. 멀티/세이브 스코프 밖이므로 → backlog. |

---

## 요약: "지금 반영" 액션 (마일스톤 2에 통합)

1. 효과/죽음 처리 시 `toList()` 순회로 CME 방지 ✅
2. 죽음 일괄 제거 후 죽음의 메아리 발행(안정화 루프) ✅
3. 카드 안정 id: `defId`(데이터) + `CardId`(인스턴스) ✅
4. UI는 도메인 `Card` 비노출 — 불변 `MinionUi/CardUi`로만 변환(마일스톤 3) ✅
5. 매 액션 후 새 `GameUiState`/리스트 생성으로 참조 변경 보장(마일스톤 3) ✅

→ 1~3은 도메인에 반영 완료(테스트 11종 통과). 4~5는 ViewModel/UI 마일스톤에서 반영.
**🟦 스코프 밖(스택/우선권/Static·Aura/임베디드 스크립트/전면 불변화)은 구현하지 않는다.**
"나중" 권고는 `review/backlog.md` 참고.
