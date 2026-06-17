# 타이밍 & 우선권 (Timing & Priority) 리서치

> 대상: 콘솔 Kotlin 카드게임(`PlayableCardGame.kt`) → Android 포팅.
> 이 게임은 **의도적으로 단순한 하스스톤 스타일 학습용 프로토타입**이다.
> 핵심 메커닉: 턴 교대, 매 턴 마나 +1(최대 10), 소환 멀미(summoning sickness),
> 공격/반격(counterattack), 피로(fatigue), 승패 판정.
> **순차 해결(sequential resolution), 상호작용 스택/체인 없음.**

---

## 1. 개요 (Overview)

턴제 카드게임의 "타이밍 & 우선권" 설계는 결국 하나의 질문으로 귀결된다:

> **"한 플레이어가 무언가를 했을 때, 상대가 그 해결(resolution) *도중에* 끼어들 수 있는가?"**

이 질문의 답에 따라 두 가지 큰 부류로 나뉜다.

1. **스택/체인 모델 (interactive / reactive)** — MTG, Yu-Gi-Oh.
   행동을 즉시 해결하지 않고 "대기열(스택)"에 쌓고, 양쪽 플레이어가 **응답 창(response window)** 을
   거쳐 *마지막에 쌓인 것부터(LIFO)* 해결한다. 끼어들기·상호작용이 게임의 핵심 재미.
2. **순차 큐 모델 (sequential queue / non-interactive)** — Hearthstone.
   능동 플레이어의 행동을 **즉시, 끝까지 해결**한다. 해결 도중 상대는 끼어들 수 없다.
   트리거는 스택이 아니라 **큐(Queue)** 에 들어가 *먼저 들어온 것부터(FIFO, "order of play")* 해결되며,
   해결이 시작된 큐는 **불변(immutable)** 이 되어 새 항목을 받지 않는다.

본 프로토타입은 명시적으로 **순차 큐 모델(하스스톤형)** 이며, 스택을 의도적으로 제거한다.
아래에서 두 모델을 비교하되, **MTG/Yu-Gi-Oh의 스택은 "비교 대상"일 뿐 채택 권장 대상이 아님**을
분명히 한다. 핵심은 **하스스톤 모델이 *무엇을 의도적으로 버렸는지*** 를 정확히 아는 것이다(§5, §6).

---

## 2. 모델별 비교 (스택 있음 vs 순차 큐)

### 2.1 MTG — Priority + Stack (LIFO)

- **우선권(Priority)**: 주문 시전·능력 발동·특수 행동을 하려면 "우선권"을 가져야 한다.
  우선권 없이는 아무 행동도 못 한다.
- **APNAP 순서**: 각 단계/스텝이 시작될 때 **능동 플레이어(Active Player)** 가 먼저 우선권을 받고,
  이어서 **비능동 플레이어(Non-Active Player)**. 동시에 발동하는 트리거들도 AP→NAP 순으로 스택에 쌓인다.
- **스택(Stack)은 LIFO**: 가장 나중에 쌓인 주문/능력이 가장 먼저 해결된다.
- **응답 창(response window)**: 스택에 무언가 있을 때 양쪽이 *번갈아* 우선권을 받아 응답(추가 주문/능력)하거나
  패스(pass)할 수 있다. **양쪽이 연속으로 패스하면** 스택 맨 위 항목 1개가 해결되고, 다시 AP가 우선권을 얻는다.
- **스택을 안 쓰는 것들**: 정적 능력(static), 마나 능력(즉시 해결), 효과 자체, 그리고 턴 기반 행동(turn-based actions).
- **빈 스택 + 양쪽 패스 = 단계/스텝 종료.**

### 2.2 Yu-Gi-Oh — Chain + SEGOC + Fast Effect Timing (LIFO)

- **체인 링크(Chain Link)**: 체인을 시작한 카드/효과가 Chain Link 1, 이후 발동마다 +1.
  **해결은 역순(LIFO)** — 마지막 체인 링크가 먼저 해결된다.
- **스펠 스피드(Spell Speed)**: Chain Link 2 이상으로 발동하려면 직전 링크와 *같거나 높은* 스펠 스피드여야 한다
  (반응성에 제약을 거는 장치).
- **SEGOC (Simultaneous Effects Go On Chain)**: 여러 트리거가 *동시에* 발동 조건을 만족하면 다음 순서로 체인에 올린다:
  1. **TP(턴 플레이어) 강제(mandatory)**
  2. **NTP(비턴 플레이어) 강제**
  3. **TP 임의(optional)**
  4. **NTP 임의**
  - 같은 분류 안에서 여러 개면 그 카드를 가진 플레이어가 순서를 고른다.
- **Fast Effect Timing**: 체인을 쌓는 동안, *가장 최근 링크를 발동하지 않은* 쪽이 응답할 권리를 갖는다.
  트리거가 모두 올라간 뒤에도 fast effect로 체인을 계속 쌓을 수 있다.

### 2.3 Hearthstone — Sequence / Queue (FIFO, 스택 없음)

- **Phase → Sequence → Event → Trigger** 구조. 능동 플레이어 행동은 **즉시 끝까지 해결**된다.
- **응답 창 없음**: 상대는 내 행동/해결 *도중에* 절대 끼어들 수 없다. "Priority" 개념 자체가 없다.
- **트리거 정렬 = "order of play" (FIFO)**: 동시 고려되는 이벤트(데미지·힐·죽음·손으로 복귀 등)나
  트리거가 여러 개면 **엔티티가 전장에 등장한 순서(오래된 것 → 새것)** 로 큐잉되어 해결된다. LIFO 아님.
  - 미니언·영웅·무기·비밀(Secret)·부착 인챈트·추가된 죽음의 메아리 모두 *하나의 order-of-play 리스트* 를 공유한다.
- **큐 불변성(Queue immutability)** — **가장 중요한 차별점**:
  > "A Queue becomes immutable once Hearthstone starts to resolve the first entry in it.
  > No new entries can be added to the Queue after this point."
  - 해결 *도중* 새로 소환된 미니언/비밀은 *그 진행 중인 큐* 에 응답할 수 없다(이후 새 Phase/Event에는 큐잉 가능).
  - 예: 여러 미니언을 한꺼번에 소환할 때, 첫 번째가 Dread Infernal이면 그 전투의 함성은 나머지 2장을 때리지 않는다 —
    걔들은 첫 소환 해결이 *끝난 뒤에야* 전장에 등장하기 때문.
- **죽음 처리(Death Creation Step)**: 데미지/파괴 이벤트 해결 후 별도의 죽음 처리 스텝에서 일괄적으로
  체력 0 이하 엔티티를 한꺼번에 죽이고, 죽음의 메아리 등 결과 트리거를 order-of-play로 큐잉한다.
  ('After ...' 트리거는 Sequence 시작 시점에 유효해야 작동.)

### 2.4 한눈 비교표

| 항목 | MTG | Yu-Gi-Oh | Hearthstone (= 본 프로토타입) |
|---|---|---|---|
| 상호작용 모델 | Stack | Chain | Queue/Sequence |
| 해결 순서 | LIFO | LIFO | **FIFO (order of play)** |
| 상대 응답 창 | 있음 (priority) | 있음 (fast effect) | **없음** |
| 동시 트리거 정렬 | APNAP | SEGOC(강제/임의 × TP/NTP) | **order of play (등장 순)** |
| 해결 중 추가 개입 | 가능(우선권) | 가능(체인 연장) | **불가(큐 불변)** |
| 반응성 제약 장치 | 없음(전부 반응) | Spell Speed | 해당 없음(반응 자체가 없음) |
| 설계 의도 | 깊은 상호작용 | 깊은 상호작용 | **단순함·예측가능성** |

---

## 3. 코드·상태머신 형태 (State Machine Shape)

### 3.1 순차 큐 모델 (권장 — 본 프로토타입)

스택/우선권 패스 루프가 없으므로 상태 기계가 매우 단순하다. 핵심은
**(a) 턴 페이즈 진행** 과 **(b) 행동 해결 시 트리거를 FIFO 큐로 처리** 두 가지뿐이다.

턴 페이즈 (하스스톤 대응, 본 프로토타입에 충분):

```
START_OF_TURN          // 마나 결정(+1, 최대 10), 빈 마나 충전, start-of-turn 트리거 큐잉
  → DRAW               // 카드 1장 뽑기 (덱 비면 FATIGUE: 누적 데미지)
  → MAIN               // 플레이어 행동 루프: 카드 내기/주문/공격 (반복)
       └ 각 행동 = resolveAction() → 트리거 큐 FIFO 해결 → 죽음 처리 → 승패 체크
  → END_OF_TURN        // end-of-turn 트리거 큐잉/해결
  → (상대 턴으로 교대)
```

행동 해결 의사코드 (스택 없음, 큐 불변성 반영):

```kotlin
fun resolveAction(action: Action) {
    val queue = ArrayDeque<Trigger>()       // FIFO
    apply(action)                            // 즉시 효과 적용 (예: 데미지)
    collectTriggers(queue)                   // 발동 트리거를 order-of-play로 수집
    // 이 시점부터 queue는 "불변": 해결 중 생긴 트리거는 새 큐로 미룬다
    val snapshot = queue.toList()
    for (t in snapshot) {
        t.resolve()                          // 상대 끼어들기 없음
    }
    processDeaths()                          // 0 이하 엔티티 일괄 제거 → 죽음의 메아리 큐잉
    checkWinLoss()                           // 양 영웅 체력 확인
}
```

핵심 포인트:
- **우선권 패스 루프가 없다** → 상태 폭발(state explosion)이 없고 AI/입력 처리가 단순.
- 트리거 정렬은 `order of play`(엔티티 등장 순) 한 가지 규칙으로 일관.
- "큐 불변성"은 *해결 시작 시 스냅샷을 떠서 그 동안엔 그 리스트만 처리* 로 구현(위 `snapshot`).

### 3.2 스택 모델 (비교용 — 채택하지 말 것)

```
모든 단계에서:  AP에게 우선권 →
  loop {
    현재 우선권자: 행동 후 스택에 push, 우선권은 상대에게  | 또는 패스
    두 플레이어 연속 패스?  ──no──▶ 계속
                              ──yes─▶ 스택 top 1개 resolve(LIFO) → AP에게 우선권 재부여
                                       스택 비었으면 단계 종료
  }
```

상태 기계가 **(우선권 보유자 × 스택 상태 × 단계)** 로 곱연산 폭증한다.
네트워크 동기화·UI 응답 창·타임아웃까지 필요해진다. 학습용 프로토타입에는 과잉.

---

## 4. 실제 게임 비교 (각 메커닉이 본 프로토타입에 어떻게 매핑되나)

| 프로토타입 메커닉 | 하스스톤 대응 | 비고 |
|---|---|---|
| 턴 교대 | 턴 단위 교대(우선권 패스 없음) | 한 쪽이 MAIN을 다 쓰고 END → 교대 |
| 마나 +1/턴, 최대 10 | 마나 크리스털 +1, 최대 10 | START_OF_TURN에서 결정·충전 |
| 소환 멀미 | "방금 나온 미니언은 공격 불가" | `canAttack = enteredTurn < currentTurn` 플래그 |
| 공격/반격 | 공격자↔방어자 *동시* 데미지 교환 | 한 Event로 양쪽 데미지 → 죽음 처리 |
| 피로(fatigue) | 빈 덱에서 드로우 시 누적 데미지 | DRAW 페이즈에서 처리 |
| 승패 판정 | 영웅 체력 0 → 패배 | 매 해결 후 `checkWinLoss()` |
| 함성/죽음의 메아리/광역 버프 | 트리거 → order-of-play 큐 | 큐 불변성 적용 |

MTG/Yu-Gi-Oh 대비, 본 프로토타입에는 **인스턴트 속도 응답·함정(트랩)·반격 카드·상대 턴 행동이 없다.**
이것은 결함이 아니라 **순차 큐 모델의 정의상 당연한 귀결**이다.

---

## 5. 하스스톤 모델이 *의도적으로 생략* 하는 것 (스코프 판단의 핵심)

> **이 섹션이 본 리서치의 핵심.** 아래는 "빠진 기능"이 아니라 **의도적 설계 선택**이다.

1. **상호작용 스택 / 체인 자체** — 행동을 대기열에 쌓아 *나중에* 해결하는 개념이 없다.
   능동 플레이어 행동은 즉시 끝까지 해결.
2. **우선권(priority) 패스 메커니즘** — "지금 누가 행동할 권리가 있는가"라는 개념이 없다.
   자기 턴엔 자기만 행동, 상대 턴엔 행동 불가.
3. **상대의 응답 창(response window)** — 내 주문/공격 *해결 도중* 상대가 끼어드는 창이 전혀 없다.
   (하스스톤의 비밀(Secret)은 *조건 충족 시 자동 발동*이지, 플레이어가 선택해 끼어드는 게 아니다.)
4. **인스턴트/카운터 속도** — MTG의 instant, Yu-Gi-Oh의 trap/quick-effect처럼 *상대 턴에* 발동하는 카드가 없다.
5. **LIFO 해결** — 나중에 쌓인 게 먼저 풀리는 역순 해결이 없다. 전부 **FIFO(order of play)**.
6. **스펠 스피드 / APNAP / SEGOC 우선권 사다리** — 반응성을 규율하는 복잡한 우선권 규칙이 불필요.
   하스스톤은 동시 트리거를 *오직 등장 순서* 하나로 결정.
7. **단계별 다중 우선권 라운드** — 한 스텝 안에서 양쪽이 여러 번 번갈아 행동하는 루프가 없다.

**대신 하스스톤이 들여온 복잡성**은 단 하나: **큐 불변성(Queue immutability)** 과
**죽음 처리 스텝(Death Creation Step)**. 즉 "해결 중 보드 상태가 바뀌어도 *이번 큐는 시작 시점 스냅샷대로* 처리"라는
규칙. 이것이 스택 없이도 결정론적(deterministic)·예측가능한 해결을 보장한다.

**스코프 적합성 결론**: 본 프로토타입이 위 1~7을 모두 생략하는 것은 **정상이며 올바른 선택**이다.
스택을 도입하면 학습용 단순성이라는 목표 자체가 깨진다. 단, **큐 불변성/죽음 처리 스냅샷**은
*아주 단순한 형태로라도* 유지해야 트리거 순서 모호성 버그를 피할 수 있다(§6).

---

## 6. 흔한 함정 (Common Pitfalls)

1. **해결 도중 컬렉션 수정 (큐 불변성 위반)**
   트리거를 해결하면서 *같은 큐* 에 새 트리거를 추가하면, 의도치 않은 연쇄·무한 루프·
   `ConcurrentModificationException`이 난다. → **해결 시작 시 스냅샷**을 떠서 처리하고,
   해결 중 생긴 트리거는 **다음 큐/시퀀스로 미룬다**(하스스톤 정확 동작).
2. **동시 트리거 순서 모호성**
   여러 트리거가 동시 발동할 때 정렬 규칙이 없으면 결과가 비결정적이 된다. → **단일 규칙(order of play,
   엔티티 등장 순)** 으로 고정. 엔티티마다 단조 증가하는 `playOrder: Long`을 부여하고 그걸로 정렬.
3. **죽음 처리 타이밍**
   데미지를 입히는 즉시 미니언을 제거하면, *동시에 죽어야 할* 미니언들의 죽음의 메아리 순서가 꼬인다.
   → 데미지 Event 해결 후 **별도 죽음 처리 스텝**에서 0 이하 엔티티를 *한꺼번에* 제거하고 트리거를 큐잉.
4. **공격/반격 데미지 동시성**
   공격자→방어자, 방어자→공격자 데미지를 순차로 적용하면, 먼저 죽은 쪽이 반격을 못 하는 버그.
   → 양쪽 데미지를 *동시에* 계산·적용한 뒤 죽음 처리.
5. **승패 체크 누락/과다**
   해결 *도중* 영웅이 0이 됐는데 다음 행동까지 체크를 안 하거나, 반대로 너무 자주 체크해 동시 패배
   (무승부)를 놓치는 경우. → **각 Sequence/해결 단위 종료 시 1회** 체크, 양쪽 동시 0이면 무승부 처리.
6. **마나 상한·충전 순서 오류**
   START_OF_TURN에서 "최대 마나 +1(상한 10)" 과 "현재 마나를 최대치로 충전"을 헷갈리거나 순서를 바꾸면
   마나가 어긋난다. → 최대치 증가 → 그 다음 현재치 = 최대치.
7. **소환 멀미 플래그 갱신 시점**
   "이번 턴에 나온 미니언" 판정을 *현재 턴 번호* 와 *등장 턴* 비교 없이 단순 boolean으로 두면,
   턴 넘어갈 때 리셋을 빠뜨려 영구 공격 불가 버그. → `enteredTurn`을 저장하고 턴 시작 시 재평가.
8. **(스택을 안 쓰는데도) "응답 창"을 기대하는 UI**
   순차 큐 모델인데 UI가 상대 응답을 기다리는 모달을 띄우면 데드락. → 능동 플레이어 행동은
   *입력 없이 끝까지* 해결되도록 흐름 설계.

---

## 7. 권장 사항 (Fit for This Prototype)

- **순차 큐 모델을 그대로 유지하라.** 스택/우선권/응답 창을 도입하지 말 것 —
  학습용 단순성·예측가능성이라는 목표에 정확히 부합한다. (MTG/Yu-Gi-Oh는 *비교 대상*일 뿐.)
- **트리거는 단일 FIFO 큐 + order-of-play 정렬**로 통일하라. LIFO·APNAP·SEGOC 같은 규칙은 불필요.
- **"큐 불변성"만은 단순하게라도 구현하라**: 해결 시작 시 스냅샷, 도중 생성 트리거는 다음 큐로.
  이 하나가 트리거 순서 버그(함정 1·2·3)를 거의 다 막아준다.
- **죽음 처리는 별도 스텝**으로 분리하고, **공격/반격 데미지는 동시 적용** 후 일괄 죽음 처리.
- 턴 페이즈는 `START_OF_TURN → DRAW → MAIN → END_OF_TURN`의 단순 상태 기계로 충분.
  단계별 다중 우선권 라운드는 만들지 말 것.
- Android 포팅 시: 해결 흐름은 **동기적·결정론적**으로 두고, UI 애니메이션만 큐 해결 결과를
  *사후에* 재생하도록 분리하면 입력/렌더링과 게임 로직이 깔끔히 분리된다.

---

## 8. 출처 URL 목록 (Sources)

**Hearthstone (순차 큐 모델 — 본 프로토타입의 기준)**
- Advanced rulebook (New Hearthstone Wiki): https://hearthstone.wiki.gg/wiki/Advanced_rulebook
- Advanced rulebook (Fandom): https://hearthstone.fandom.com/wiki/Advanced_rulebook
- Advanced rulebook project / Draft1: https://hearthstone.wiki.gg/wiki/Hearthstone_Wiki:Advanced_rulebook_project/Draft1

**Magic: The Gathering (스택 모델 — 비교 대상)**
- Stack (MTG Wiki): https://mtg.fandom.com/wiki/Stack
- Turn structure (MTG Wiki): https://mtg.fandom.com/wiki/Turn_structure
- Timing and priority (MTG Wiki): https://mtg.fandom.com/wiki/Timing_and_priority
- MTG Priority Guide (APNAP / turn structure): https://tcgprotectors.com/blogs/magic-the-gathering-blog/mtg-priority-guide-2025-apnap-turn-structure-strategy
- Everything You Need to Know About Priority (Draftsim): https://draftsim.com/mtg-priority/
- MTG Phases – Turn Structure & Priority (Tabletop Meta): https://www.tabletopmeta.com/blog/magic-the-gathering-phases

**Yu-Gi-Oh (체인/SEGOC 모델 — 비교 대상)**
- Fast Effect Timing (공식 yugioh-card.com): https://www.yugioh-card.com/en/play/fast-effect-timing/
- Fast effect timing (Yugipedia): https://yugipedia.com/wiki/Fast_effect_timing
- Chain (Yugipedia): https://yugipedia.com/wiki/Chain
- Simultaneous Effects (Yugipedia): https://yugipedia.com/wiki/Simultaneous_Effects
- Simultaneous Effects Go On Chain (Fandom): https://yugioh.fandom.com/wiki/Simultaneous_Effects_Go_On_Chain
- Demystifying Rulings, Part 3: SEGOC (YGOrganization): https://ygorganization.com/learnrulingspart3/
- Demystifying Rulings, Part 7: Fast Effect Timing (YGOrganization): https://ygorganization.com/learnrulingspart7/
- Guide: What is Chain Link and SEGOC (cardsrealm): https://yugioh.cardsrealm.com/en-us/articles/guide-what-is-chain-link-and-segoc-in-yu-gi-oh
