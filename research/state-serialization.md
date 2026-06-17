# 상태 관리 & 직렬화 리서치 — 턴제 카드 게임

> 대상: 콘솔 Kotlin 학습용 프로토타입 `PlayableCardGame.kt`(Hearthstone 스타일)를
> Android Jetpack Compose + MVVM(ViewModel + StateFlow)으로 포팅.
> HANDOFF 핵심 항목: **"가변 도메인 상태 → 불변 UI 스냅샷(GameUiState)"**,
> `GameAction` 명령 패턴(PlayCard / AttackMinion / AttackHero / EndTurn) 기반 디스패치.
>
> 이 문서의 모든 사례는 **"Kotlin 도메인 모델 + ViewModel StateFlow 스냅샷 구조"에 대응하는 형태**로 정리한다.

---

## 1. 개요 (Overview)

턴제 카드 게임은 본질적으로 **상태 기계(state machine)** 다. "현재 게임 상태"가 단 하나
존재하고, 플레이어의 행동(카드 내기, 공격, 턴 종료)이 그 상태를 다음 상태로 전이시킨다.
웹/게임 업계에서 검증된 패턴은 다음 네 가지로 수렴한다.

1. **단일 진실 공급원(Single Source of Truth) + 불변 상태** — Redux/Flux 계열.
2. **명령(Command) 패턴 + 액션 디스패치** — 상태 변경은 오직 "액션을 디스패치"해야만 발생.
3. **상태 스냅샷 + UI 반영** — 불변 스냅샷의 참조 동일성(reference identity)으로 변경 감지.
4. **이벤트 소싱(Event Sourcing) / 결정론(Determinism)** — 저장·로드·리플레이·온라인 동기화·undo/redo.

Compose 진영의 권장 아키텍처가 이 패턴들과 정확히 일치한다. Android 공식 문서는
"Compose의 UI는 불변(immutable)이며 그려진 뒤에는 바꿀 수 없다. 제어할 수 있는 것은 UI의
*상태*뿐이다"라고 명시하고, **상태는 아래로(state down), 이벤트는 위로(events up)** 흐르는
단방향 데이터 흐름(UDF)을 제시한다. ViewModel이 단일 진실 공급원이 되어 불변 스냅샷을
`StateFlow`로 노출하고, UI는 이벤트(=우리 경우 `GameAction`)를 위로 전달한다.
([Android Compose Architecture](https://developer.android.com/develop/ui/compose/architecture),
[Compose State](https://developer.android.com/develop/ui/compose/state))

이 프로토타입의 도메인(게임 엔진)은 순수 Kotlin으로 두고, ViewModel이 그 도메인 상태를
`GameUiState`라는 불변 스냅샷으로 매핑해 `StateFlow`로 흘려보내는 것이 정석이다.

---

## 2. 핵심 패턴 (불변 · 명령 · 스냅샷)

### 2.1 단일 진실 공급원 + 불변 상태 (Redux-style)

Redux의 핵심 원칙은 애플리케이션 전체 상태를 **하나의 불변 상태 객체(store)** 에 담는
것이다. 상태는 직접 수정할 수 없고, **"무슨 일이 일어났는지 기술하는 action을 emit"** 하고
**reducer**(액션에 따라 다음 상태를 결정하는 함수)를 통해서만 바뀐다. reducer는 항상 기존
상태를 변형(mutate)하지 않고 **새 상태 객체를 반환**해야 한다.
([GeeksforGeeks: Immutable State in Reducers](https://www.geeksforgeeks.org/how-to-handle-immutable-state-in-redux-reducers/),
[DEV: Introduction to Redux Pattern](https://dev.to/thisdotmedia/introduction-to-redux-pattern-59f3))

> **Kotlin 대응:** `GameState`(또는 `GameUiState`)를 `data class`로 만들고, 모든 변경은
> `state.copy(...)`로 새 인스턴스를 만든다. 컬렉션은 `List`/`Map`(불변 인터페이스)으로 노출하고
> 내부 `MutableList`를 그대로 새지(leak) 않게 한다. ViewModel의 `MutableStateFlow`가 store,
> 공개 `StateFlow`가 읽기 전용 뷰가 된다.

MVI(Model-View-Intent)는 이 Redux식 단방향 흐름을 모바일에 맞춘 형태로, MVVM 대비
**상태를 하나의 불변 객체로 모으고 의도(Intent/Action)로만 변경**한다는 점이 강조된다.
([DZone: MVI vs MVVM](https://dzone.com/articles/compose-architecture-mvi-vs-mvvm),
[Medium: Redux-like architecture / MVI](https://medium.com/@mockreader/redux-like-architecture-as-a-state-management-mvi-baaae19532b2))

### 2.2 명령(Command) 패턴 + 액션 디스패치

명령 패턴은 "행동"을 객체로 캡슐화한다. 게임 코드에서 입력 처리·실행·기록을 분리하면
**디버깅용 "타임머신"(상태 되감기)과 리플레이**가 가능해진다. 모든 상태 변경을 명령 객체로
표현하면 명령 스트림을 기록·재생·역재생할 수 있다.
([Medium/GameDev Architecture: Command pattern & time machine](https://medium.com/gamedev-architecture/decoupling-game-code-via-command-pattern-debugging-it-with-time-machine-2b177e61556c))

boardgame.io는 이를 가장 깔끔하게 보여준다. **"move(이동/행동)는 항상 불변이며 외부 상태에
의존하거나 부수효과를 일으키지 않는다. 라이브러리는 Redux로 구현되어 있고, 각 move는
reducer이므로 순수(pure)해야 한다."** RNG 같은 비결정 요소는 상태/서버에 가둔다.
([boardgame.io](https://github.com/boardgameio/boardgame.io),
[Beginner's Guide to boardgame.io](https://jhcheung.medium.com/beginners-guide-to-boardgame-io-19dd6c5c9977),
[boardgame.io random.md](https://github.com/boardgameio/boardgame.io/blob/main/docs/documentation/random.md))

> **Kotlin 대응:** `sealed interface GameAction { PlayCard; AttackMinion; AttackHero; EndTurn }`.
> 엔진에 `fun reduce(state: GameState, action: GameAction): GameState` 단일 진입점을 둔다.
> UI는 `viewModel.dispatch(action)`만 호출하고, 상태는 오직 이 함수를 통해서만 생성된다.
> 이는 "상태 변경은 액션 디스패치로만 발생"이라는 HANDOFF 요구와 1:1 매핑된다.

### 2.3 상태 스냅샷 + UI 반영 (참조 동일성)

Compose는 **불변 상태 + 값 동등성(value equality)** 덕분에 재구성(recomposition) 여부를
"싸게" 판단한다. 화면에 필요한 모든 것을 하나의 불변 `data class` 스냅샷으로 묶으면, UI는
의미 있는 변경이 있을 때만 반응하고 컴파일러는 불필요한 재구성을 건너뛴다. ViewModel은
변경 시 **완전히 새로운 "스냅샷"** 을 emit하므로 스레드 안전하고 예측 가능하며, UI가 받은
뒤에는 렌더 도중 변경되지 않는다.
([Medium/Deloitte: Compose State Management](https://medium.com/deloitte-uk-cloud-blog/jetpack-compose-state-management-the-three-musketeers-8e5e0f4cab40),
[Zignuts: Compose UI Architecture](https://www.zignuts.com/blog/jetpack-compose-ui-architecture))

핵심은 **참조 동일성**이다. `copy()`로 새 인스턴스를 만들면 참조가 바뀌어 `StateFlow`가 새
값으로 인식하고 방출한다. 반대로 내부 컬렉션을 in-place로 변형하면 참조가 그대로라
`StateFlow.value = x`가 **동일 객체로 간주되어 emit이 누락**될 수 있다(아래 5장 함정 참고).
`StateFlow`는 값 기반 동등성(`equals`)으로 중복 방출을 걸러내므로, `data class`의 정확한
`equals`/`copy` 사용이 정상 UI 갱신의 전제다.
([StateFlow API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/))

### 2.4 효과 해소(effect resolution)를 그래프/DFS로 처리하기

Hearthstone/MTG 류의 트리거 효과는 **스택/큐를 통한 깊이 우선(DFS) 해소**로 모델링된다.
MTG에서는 트리거된 능력이 효과 스택에 올라가고, "모든 플레이어가 연속으로 우선권을 넘길
때까지" 스택 맨 위 항목이 해소되지 않는다 — 즉 **사용자 입력을 기다리며 일시정지(pause)** 하는
지점이 존재한다. "may"가 붙은 효과는 해소 중 플레이어에게 선택권을 준다.
([MTG Wiki: Triggered ability](https://mtg.fandom.com/wiki/Triggered_ability),
[MTG Wiki: Turn-based action](https://mtg.fandom.com/wiki/Turn-based_action),
[Grand Archive Rules: Triggered Abilities](https://rules.gatcg.com/game-mechanics/game-mechanics-abilities/abilities-triggered-abilities))

boardgame.io는 이를 구현 수준에서 보여준다. move 이후 트리거되는 후속 처리는
`flow.processMove`를 통해 진행되고, **이벤트는 큐에 쌓여 move 이후에 트리거**된다(이벤트를
move 안에서 먼저 호출해도 G의 변경이 먼저 적용된 뒤 이벤트가 발화). `activePlayers`/`stages`는
"지금 누가, 어떤 단계에서 행동할 수 있는가"를 추적하여 **입력 대기 지점**을 표현한다. 한 플레이어가
move를 하면 `activePlayers`에서 제거되고, 비면 `null`이 된다.
([boardgame.io events.md](https://github.com/boardgameio/boardgame.io/blob/main/docs/documentation/events.md),
[boardgame.io stages.md](https://github.com/boardgameio/boardgame.io/blob/main/docs/documentation/stages.md),
[Phases and Stages](https://nicolodavis.com/blog/boardgame.io-0.33/))

> **Kotlin 대응:** 효과 해소를 재귀(DFS) 또는 명시적 스택(`ArrayDeque<Effect>`)으로 처리한다.
> "사용자 입력이 필요한 효과"를 만나면 상태를 `GameState(pendingChoice = ...)` 같은
> **일시정지 상태**로 만들어 emit하고 반환한다. UI가 선택 결과를 새 `GameAction`(예:
> `ResolveChoice`)으로 디스패치하면 엔진이 스택 해소를 **이어서** 재개한다. 이렇게 하면
> 한 호출 안에서 블로킹/대기하지 않고도(코루틴/콜백 없이) 멈췄다 재개하는 흐름이 된다 —
> 이 프로토타입에 권장되는 방식.

---

## 3. 직렬화 전략 (Serialization)

### 3.1 무엇을 직렬화할 것인가 — 스냅샷 vs 이벤트 로그

두 가지 보완적 접근:

- **상태 스냅샷 직렬화 (save/load):** 현재 `GameState`를 통째로 JSON 등으로 저장. 단순하고
  로드가 빠르다.
- **이벤트 소싱 (replay/online sync/undo):** 상태를 직접 저장하지 않고 **모든 변경을 불변
  이벤트(action) 시퀀스로 기록**, 빈 상태에서 재생해 현재 상태를 재구성한다. 이벤트가
  불변이므로 임의 버전의 상태를 부분 재생으로 재현할 수 있어 **무제한 undo/redo, 감사(audit),
  디버깅 리플레이**가 가능하다. 재생 비용은 **주기적 스냅샷**으로 완화한다(가장 최근 스냅샷을
  로드하고 그 이후 이벤트만 재생).
  ([Martin Fowler: Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html),
  [Azure Architecture: Event Sourcing](https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing),
  [Eric Jinks: Undo/redo with event sourcing](https://ericjinks.com/blog/2025/event-sourcing/))

> **이 프로토타입 권장:** `GameAction`이 이미 직렬화 가능한 명령이므로, **액션 로그 = 리플레이
> 데이터**가 자연스럽게 따라온다. 1차로 `GameState` 스냅샷 저장(save/load)만 구현하고,
> undo/리플레이가 필요해지면 액션 로그 재생을 얹는 단계적 접근이 학습 프로토타입에 적합.

### 3.2 결정론(Determinism)이 직렬화/동기화에 주는 이점

결정론적 시뮬레이션에서는 **같은 입력 집합을 받은 두 복제본이 같은 상태로 수렴**한다(알고리즘이
결정론적이고 유한·비순환 선행 관계를 따르며 수신 순서에 의존하지 않기 때문). 따라서 온라인
동기화에서 **전체 상태가 아니라 입력(명령)만 전송**해도 된다 — 클라이언트는 "카드 X 내기",
"턴 종료" 같은 입력만 보내고, 서버가 큐에 넣어 브로드캐스트하면 모든 클라이언트가 동일한
결정론적 시뮬레이션으로 로컬에 적용한다(deterministic lockstep). RNG는 시드를 상태에 넣어
서버/엔진에 가둬야 결정론이 깨지지 않는다.
([DevelopersVoice: Real-time card games in .NET](https://developersvoice.com/blog/practical-design/realtime-card-games-net-architecture-guide/),
[DEV: Scalable Real-Time Multiplayer Card Games](https://dev.to/krishanvijay/building-scalable-real-time-multiplayer-card-games-3kn6),
[arXiv: Undo/Redo for Replicated Registers](https://arxiv.org/pdf/2404.11308),
[boardgame.io random.md](https://github.com/boardgameio/boardgame.io/blob/main/docs/documentation/random.md))

### 3.3 Kotlin 구현 메모

- `kotlinx.serialization`으로 `@Serializable data class GameState`와
  `@Serializable sealed interface GameAction`을 직렬화. sealed 계층은 **다형성(polymorphic)
  직렬화**로 `type` 판별자가 자동 부여된다.
  ([kotlinx polymorphism.md](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md),
  [Baeldung: kotlinx inheritance](https://www.baeldung.com/kotlin/kotlinx-serialization-inheritance))
- **모든 하위 타입에 `@Serializable` 부여, 컴파일타임 타입을 다형성 기반 타입으로** 직렬화해야
  필드 누락을 피할 수 있다(아래 함정 참고).

---

## 4. 실제 엔진 사례 (Real engines)

| 엔진 / 사례 | 불변 store | 명령/액션 | DFS·큐 해소 | 입력 대기(pause) | 직렬화/리플레이 | Kotlin 구조 대응 |
|---|---|---|---|---|---|---|
| **boardgame.io** | Redux + Immer 불변 G | move = pure reducer | `flow.processMove`, 이벤트 큐 | `activePlayers`/`stages` | 게임 로그 타임트래블 | `GameState data class` + `reduce(state, action)` + 액션 로그 |
| **Bang! 엔진 (IonGyth/bang_engine)** | "Immutable Programming(함수형 Python)"에서 영감받은 불변 설계 | 커맨드라인 입력 → 액션 | 효과 순차 해소 | 콘솔 입력 대기 | — | 순수 도메인 + 콘솔→`GameAction` |
| **Bang! 멀티플레이(MattSkala/bang-game, C++)** | 서버 권위 상태 | 클라→서버 입력 메시지 | 서버에서 상태 변경 이벤트 | 동기 채널로 차례 대기 | TCP 상태 변경 이벤트 스트림 | 추후 온라인화 시 deterministic lockstep 참고 |
| **MTG / Grand Archive 규칙(룰셋)** | 게임 상태 | 주문/능력 | **효과 스택 DFS 해소** | 우선권(priority) 패스까지 pause, "may" 선택 | — | `ArrayDeque<Effect>` + `pendingChoice` 상태 |
| **이벤트 소싱(Fowler/Azure)** | 이벤트로 재구성 | 도메인 이벤트 | 순차 재생 | — | 스냅샷+이벤트, undo/redo | 액션 로그 + 주기적 스냅샷 |

- boardgame.io는 **"State Management and Multiplayer Networking for Turn-Based Games"** 를
  표방하며, 이 프로토타입의 목표(턴제 + 상태관리 + 추후 멀티플레이)와 가장 직접적으로 겹친다.
  ([boardgame.io](https://github.com/boardgameio/boardgame.io))
- Bang! 계열 GitHub 구현들: 함수형 불변 설계 영감
  ([IonGyth/bang_engine](https://github.com/IonGyth/bang_engine)),
  C++ 클라이언트-서버 권위 모델
  ([MattSkala/bang-game](https://github.com/MattSkala/bang-game)),
  C# 네트워크 구현([zjevik/Bang-card-game](https://github.com/zjevik/Bang-card-game)).

---

## 5. 흔한 함정 (Common Pitfalls)

1. **공유 가변 상태 / 에일리어싱(aliasing).** 두 변수가 같은 객체를 참조하면 한쪽 변경이
   다른 쪽에 새어 나간다. 도메인의 `MutableList`를 그대로 UI 스냅샷에 넣으면, 엔진이 다음 턴에
   리스트를 in-place 변경할 때 **이미 emit한 "과거 스냅샷"까지 함께 바뀐다**(리플레이/undo가 깨짐).
   방어적 복사(`toList()`) 또는 불변 컬렉션을 사용한다.
   ([Kotlin: Shared mutable state](https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html),
   [Substack: Collection aliasing code smell](https://maxicontieri.substack.com/p/code-smell-266-collection-aliasing))

2. **참조가 안 바뀌어 UI가 갱신되지 않음.** `StateFlow`는 값 동등성으로 중복을 거른다.
   내부 객체를 변형하고 `_state.value = sameRef`로 재할당하면 **동일 객체로 간주되어 emit이
   누락**될 수 있다. 항상 `copy()`로 새 인스턴스를 만든다.
   ([StateFlow API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/))

3. **MutableStateFlow의 read-modify-write 경쟁.** `_state.value = _state.value.copy(...)`를
   동시 호출하면 갱신이 유실될 수 있다. **`_state.update { it.copy(...) }`** 로 원자적 갱신을 한다.
   ([Medium/Geek Culture: Atomic Updates on MutableStateFlow](https://medium.com/geekculture/atomic-updates-with-mutablestateflow-dc0331724405),
   [Kotlin: Shared mutable state & concurrency](https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html))

4. **동시성 / 디스패치 경쟁.** 여러 코루틴/스레드가 상태를 동시에 변경하면 재현 어려운 버그가
   생긴다. **상태 변경을 단일 디스패처(메인 스레드 또는 단일 액터/Mutex)로 직렬화**하고,
   리듀서를 순수하게 유지한다.
   ([Kotlin: Shared mutable state & concurrency](https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html),
   [Kotlin Server Squad: Synchronizing shared state](https://kotlinserversquad.com/efficient-synchronization-of-shared-state-with-coroutines/))

5. **직렬화 시 필드 누락(sealed/다형성).** kotlinx.serialization 1.0 이후 다형성 sealed 직렬화에서
   하위 타입의 일부 프로퍼티가 출력에서 빠지는 사례가 보고됨. **모든 하위 타입에 `@Serializable`,
   base는 sealed + `@Serializable`, 직렬화 시 컴파일타임 타입을 다형성 기반 타입으로** 지정해야 한다.
   미등록 다형성 타입은 보안·반사 조회 문제를 피하기 위해 사전 등록이 필요하다.
   ([kotlinx Issue #1202: Polymorphic serialization skips fields](https://github.com/Kotlin/kotlinx.serialization/issues/1202),
   [Medium: kotlinx polymorphism mistakes](https://medium.com/@kerry.bisset/kotlin-serialization-json-mistakes-i-made-with-polymorphism-and-more-e8ae367dc90a),
   [kotlinx polymorphism.md](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md))

6. **스키마 진화 시 호환성.** 저장 포맷에 새 필드가 추가되면 구버전 세이브 로드가 깨진다.
   **기본값(default)** 을 둔 nullable/옵셔널 필드와 `ignoreUnknownKeys = true`로 미래/과거
   호환을 확보한다.
   ([kotlinx polymorphism.md](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md))

7. **비결정성으로 리플레이/동기화가 깨짐.** `Math.random()`/시스템 시계 등 외부 상태에 의존하면
   동일 입력이 다른 결과를 낸다. **RNG 시드를 상태에 포함**시켜 결정론을 보존한다.
   ([boardgame.io random.md](https://github.com/boardgameio/boardgame.io/blob/main/docs/documentation/random.md))

---

## 6. 이 프로토타입에 대한 권장(요약)

- **도메인(순수 Kotlin) + ViewModel 스냅샷 분리.** 게임 엔진은 `GameState`(불변 `data class`)와
  `reduce(state, action): GameState` 단일 진입점. ViewModel이 도메인 상태를 `GameUiState`로
  매핑해 `StateFlow`로 노출 — HANDOFF의 "가변 상태 → 불변 UI 스냅샷"과 직결.
- **`sealed interface GameAction`** 으로 명령 패턴 구현, UI는 `dispatch(action)`만 호출. 상태는
  오직 디스패치로만 생성.
- **갱신은 항상 `copy()` + `_state.update { }`** (참조 동일성 보장 + 원자성). 내부 컬렉션은
  방어적 복사/불변 노출로 에일리어싱 차단.
- **효과 해소는 명시적 스택/DFS + `pendingChoice` 일시정지 상태.** 입력 필요 시 멈춘 상태를
  emit하고, `ResolveChoice` 액션으로 재개. 학습 프로토타입에 가장 단순하고 안전.
- **직렬화는 단계적으로:** 1차 `GameState` 스냅샷 save/load(kotlinx.serialization, 모든 sealed
  하위 타입 `@Serializable`, 기본값 + `ignoreUnknownKeys`). undo/리플레이/온라인이 필요해지면
  **액션 로그 재생(이벤트 소싱) + 주기적 스냅샷**을 얹고, RNG 시드를 상태에 포함해 결정론 확보.
- **참조 모델은 boardgame.io** 가 가장 잘 맞는다(턴제 + Redux식 불변 + move 리듀서 + 이벤트 큐 +
  단계별 입력 대기 + 로그 타임트래블). 온라인화 단계에서 deterministic lockstep(.NET/C++ Bang!)
  참고.

---

## 출처 URL 목록 (Sources)

**불변 상태 / Redux / MVI**
- https://www.geeksforgeeks.org/how-to-handle-immutable-state-in-redux-reducers/
- https://dev.to/thisdotmedia/introduction-to-redux-pattern-59f3
- https://medium.com/@mockreader/redux-like-architecture-as-a-state-management-mvi-baaae19532b2
- https://dzone.com/articles/compose-architecture-mvi-vs-mvvm

**Compose / StateFlow / MVVM 스냅샷**
- https://developer.android.com/develop/ui/compose/architecture
- https://developer.android.com/develop/ui/compose/state
- https://medium.com/deloitte-uk-cloud-blog/jetpack-compose-state-management-the-three-musketeers-8e5e0f4cab40
- https://www.zignuts.com/blog/jetpack-compose-ui-architecture
- https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/

**명령 패턴 / 게임 엔진**
- https://medium.com/gamedev-architecture/decoupling-game-code-via-command-pattern-debugging-it-with-time-machine-2b177e61556c
- https://github.com/boardgameio/boardgame.io
- https://jhcheung.medium.com/beginners-guide-to-boardgame-io-19dd6c5c9977
- https://github.com/boardgameio/boardgame.io/blob/main/docs/documentation/events.md
- https://github.com/boardgameio/boardgame.io/blob/main/docs/documentation/stages.md
- https://github.com/boardgameio/boardgame.io/blob/main/docs/documentation/random.md
- https://nicolodavis.com/blog/boardgame.io-0.33/

**효과 해소 (DFS / 스택 / 입력 대기)**
- https://mtg.fandom.com/wiki/Triggered_ability
- https://mtg.fandom.com/wiki/Turn-based_action
- https://rules.gatcg.com/game-mechanics/game-mechanics-abilities/abilities-triggered-abilities

**Bang! 엔진 사례**
- https://github.com/IonGyth/bang_engine
- https://github.com/MattSkala/bang-game
- https://github.com/zjevik/Bang-card-game

**직렬화 / 이벤트 소싱 / 결정론 / 리플레이 / undo-redo**
- https://martinfowler.com/eaaDev/EventSourcing.html
- https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing
- https://ericjinks.com/blog/2025/event-sourcing/
- https://prideout.net/blog/undo_system/
- https://arxiv.org/pdf/2404.11308
- https://developersvoice.com/blog/practical-design/realtime-card-games-net-architecture-guide/
- https://dev.to/krishanvijay/building-scalable-real-time-multiplayer-card-games-3kn6

**kotlinx.serialization 함정**
- https://github.com/Kotlin/kotlinx.serialization/issues/1202
- https://medium.com/@kerry.bisset/kotlin-serialization-json-mistakes-i-made-with-polymorphism-and-more-e8ae367dc90a
- https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md
- https://www.baeldung.com/kotlin/kotlinx-serialization-inheritance

**동시성 / 공유 가변 상태 함정**
- https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html
- https://medium.com/geekculture/atomic-updates-with-mutablestateflow-dc0331724405
- https://kotlinserversquad.com/efficient-synchronization-of-shared-state-with-coroutines/
- https://maxicontieri.substack.com/p/code-smell-266-collection-aliasing
