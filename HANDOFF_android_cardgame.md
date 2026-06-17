# HANDOFF: 콘솔 카드게임 → 안드로이드 앱화 (Jetpack Compose)

> Claude Code 작업 지시서. 콘솔에서 도는 Kotlin 카드게임을 안드로이드 네이티브 앱으로 포팅한다.
> **원본 코드: `PlayableCardGame.kt`를 이 저장소에 함께 제공할 것.** (참고: `MiniCardGame.kt`는 동일 도메인의 비대화형 데모일 뿐, 앱의 토대는 `PlayableCardGame.kt`다.)

---

## 0. 목표 & 한 줄 원칙

- **목표**: 폰에 설치해 끝까지 1판(승/패) 플레이 가능한, 사람 vs AI 1대1 카드 배틀 앱.
- **한 줄 원칙**: **게임 로직(도메인)은 그대로 재사용하고, UI만 Compose로 새로 짠다.** 우리가 만든 도메인 모델은 이미 UI에 독립적이라 이게 가능하다.
- **스코프**: 학습/포트폴리오용. 멀티플레이·서버·과금·계정 없음. 단일 모듈, 오프라인 단일 플레이어.

---

## 1. 기술 스택 (결정 사항)

| 항목 | 결정 |
|---|---|
| 언어 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 아키텍처 | MVVM (ViewModel + `StateFlow`) |
| 비동기 | Kotlin Coroutines (AI 턴 연출용) |
| 모듈 | **단일 모듈**로 시작 (멀티모듈 금지 — 학습 스코프) |
| 외부 라이브러리 | 최소화. Compose/Material3/Coroutines/Lifecycle-ViewModel 외 추가 금지 |

> **버전은 하드코딩하지 말 것.** 작업 시작 시 AGP / Kotlin / Compose BOM / Compile·Target SDK의 **현재 안정 버전을 직접 확인**해 적용하고, 무엇을 적용했는지 PR 설명에 명시할 것.

---

## 2. 아키텍처 (3계층, 의존성 방향 엄수)

```
UI (Compose)  ─────►  ViewModel  ─────►  domain (순수 Kotlin)
   화면/탭          상태 변환·턴 흐름      게임 규칙 (android 의존성 0)
```

- **domain**: 안드로이드 의존성 **0**. `android.*` import 금지. (`kotlin.random.Random`, `kotlinx.coroutines`는 허용.) 단위 테스트로 한 판 시뮬레이션이 가능해야 한다.
- **presentation/ViewModel**: 도메인 엔진을 보유. 도메인의 가변 상태를 **불변 UI 스냅샷**(`StateFlow<GameUiState>`)으로 변환해 노출. UI에서 올라온 의도(액션)를 엔진에 전달하고 상태를 재발행. 턴 흐름(사람 입력 대기 ↔ AI 자동 진행)을 오케스트레이션.
- **UI**: 상태를 그리고 탭을 액션으로 바꿔 ViewModel에 전달하기만 한다. 게임 규칙을 절대 갖지 않는다.

---

## 3. 도메인 리팩터링 (★ 이 문서의 핵심)

콘솔 코드에는 앱에서 못 쓰는 3가지가 있다. 아래대로 고친다.

### 3.1 `println` 제거 → 로그 콜백 주입
엔진 곳곳의 `println`(공격·효과·소환 내레이션)은 콘솔로 나간다. 이를 UI 로그로 보내야 한다.

- `GameEngine` 생성자에 로거 콜백을 주입:
  ```kotlin
  class GameEngine(
      val state: GameState,
      val human: Player,
      private val log: (String) -> Unit = ::println, // 기본은 테스트용 println
  )
  ```
- 본문의 모든 `println(...)` → `log(...)` 로 치환.
- ViewModel은 UI 로그 싱크를 콜백으로 넘긴다(아래 GameUiState.logLines에 누적).
- (대안) 로그 메시지를 `EventBus`의 새 이벤트로 발행하고 ViewModel이 구독해도 됨. 단순함을 위해 콜백 방식을 기본으로 한다.

### 3.2 `readLine` 제거 → **GameAction 명령 패턴** (가장 중요)
콘솔의 `humanTurn`/`aiTurn` 루프는 `readLine` 동기 입력에 묶여 있어 앱에서 작동하지 않는다. 사람 입력(탭)과 AI 입력을 **하나의 액션 타입으로 통일**한다.

```kotlin
sealed class GameAction {
    data class PlayCard(val card: Card) : GameAction()
    data class AttackMinion(val attacker: Card, val target: Card) : GameAction()
    data class AttackHero(val attacker: Card) : GameAction()
    object EndTurn : GameAction()
}
```

엔진을 "한 번에 하나의 액션을 적용하는" 형태로 재구성:

- `fun apply(action: GameAction)` — 액션 하나를 실행하고 상태를 in-place로 갱신 (기존 playCard/attackMinion/attackHero 로직을 여기로 이동).
- `fun legalActions(): List<GameAction>` — 현재 턴 플레이어가 할 수 있는 합법 액션 목록(낼 수 있는 카드, 공격 가능한 미니언×대상). UI 활성/비활성 표시에 사용.
- `fun startTurn(player)`, `fun isGameOver(): Boolean`, `fun winner(): Player?` — 기존 로직 유지.
- **AI**: 즉시 실행하지 말고 **계획만** 반환한다.
  ```kotlin
  fun planAiTurn(player: Player): List<GameAction>
  // 규칙: 낼 수 있는 가장 비싼 카드부터(반복) → 준비된 미니언 전원 적 영웅 공격 → EndTurn
  ```
  ViewModel이 이 리스트를 하나씩 `apply`하며 사이에 `delay`를 넣어 연출한다(3.4).

> 기존 게임 규칙(턴 교대·마나 +1(최대 10)·소환 멀미·반격·탈진·승패 판정)은 **그대로 보존**한다. 리팩터링은 "실행 진입점"만 바꾸는 것이지 규칙을 바꾸는 게 아니다.

### 3.3 가변 상태 → 불변 UI 스냅샷
엔진은 `Card.health` 등을 in-place로 바꾼다. Compose는 **새 인스턴스**가 emit돼야 리컴포즈한다. 동일 참조를 emit하면 화면이 갱신되지 않는다.

- ViewModel이 매 액션 후 도메인 상태를 읽어 `GameUiState`(불변 data class)로 **복사**해 `StateFlow`에 emit.
  ```kotlin
  data class GameUiState(
      val myHeroHp: Int, val enemyHeroHp: Int,
      val myMana: Int, val myMaxMana: Int,
      val myField: List<MinionUi>, val enemyField: List<MinionUi>,
      val myHand: List<CardUi>,
      val enemyHandCount: Int, val myDeckCount: Int, val enemyDeckCount: Int,
      val logLines: List<String>,
      val isMyTurn: Boolean,
      val phase: Phase,            // MY_TURN, AI_TURN, GAME_OVER
      val winnerName: String? = null,
      val selectedAttacker: CardId? = null, // 공격 대상 선택 모드용
  )
  ```
- `MinionUi`/`CardUi`는 표시에 필요한 값 + 안정적 `id` + `canAttack`/`affordable` 플래그를 담는 가벼운 불변 모델. (도메인 `Card`를 UI에 직접 노출하지 말 것.)

### 3.4 AI 턴 연출 (코루틴)
콘솔에선 AI 턴이 즉시 끝나도 됐지만, 앱에선 사람이 볼 수 있게 천천히 진행돼야 한다.

- ViewModel의 `viewModelScope`에서:
  ```
  planAiTurn() 으로 액션 목록 확보
  for (action in actions) { engine.apply(action); emitSnapshot(); delay(~600ms) }
  ```
- AI 턴 동안 사람 입력은 막는다(phase == AI_TURN이면 UI 비활성).

---

## 4. UI 명세 (Compose)

### 4.1 화면 레이아웃 (세로, 한 손 조작)
```
┌───────────────────────────────┐
│ 적 영웅 HP · 손패수 · 덱수      │  ← EnemyHeroBar
│ [적 미니언] [적 미니언] ...      │  ← EnemyFieldRow
├───────────────────────────────┤
│  게임 로그 (스크롤, 최신 하단)   │  ← GameLogPanel (가운데)
├───────────────────────────────┤
│ [내 미니언▶] [내 미니언] ...     │  ← MyFieldRow
│ 내 영웅 HP · 마나 X/Y           │  ← MyHeroBar
│ [손패][손패][손패] ...           │  ← HandRow (가로 스크롤)
│              [턴 종료]          │  ← EndTurnButton
└───────────────────────────────┘
```

### 4.2 컴포넌트
- `HeroBar(name, hp, handCount?, deckCount?)`
- `MinionView(MinionUi)` — 공격력/체력, **공격 가능 시 글로우 하이라이트**, 죽기 직전 강조.
- `HandCardView(CardUi)` — 코스트 뱃지, **마나 부족 시 흐리게(비활성)**.
- `ManaIndicator(current, max)` — 마나 결정(crystal) 형태 권장.
- `GameLogPanel(lines)` — 자동 스크롤.
- `EndTurnButton(enabled = isMyTurn)`
- `GameOverDialog(winnerName, onRestart)`

### 4.3 상호작용 모델
- **손패 탭**: 낼 수 있으면 즉시 `PlayCard`. (대상 지정 주문은 이번 버전 범위 밖 — 콘솔과 동일하게 고정 대상 유지.)
- **내 미니언 탭**: "공격 모드" 진입(`selectedAttacker` 설정) → 적 미니언 탭 = `AttackMinion`, 적 영웅 탭 = `AttackHero`. 빈 곳/같은 미니언 재탭 = 취소.
- **턴 종료 버튼**: `EndTurn`.
- AI 턴 중에는 모든 입력 비활성.

### 4.4 디자인 방향 (frontend-design 원칙 적용 — 기본값 회피)
**Material 3 기본 보라/다이내믹 컬러를 그대로 쓰지 말 것.** 카드 배틀이라는 주제에 맞는 의도적 정체성을 택한다.

- **시작 제안(작업자가 한 패스 다듬어 확정)**: 깊은 슬레이트/페트롤(petrol)색 전장 바탕 + 마나·활성요소엔 황동/골드 액센트 + 데미지엔 절제된 크림슨. 카드는 바탕과 대비되는 밝고 선명한 패널.
- **타이포**: 카드 이름엔 개성 있는(약간 콘덴스드한) 디스플레이 폰트, 공격력/체력 숫자는 굵고 큰 수치 전용 스타일. 본문은 가독성 좋은 산세리프.
- **시그니처 요소 하나**: "미니언 카드"의 좌하단 공격력/우하단 체력 코너 표기 + **공격 가능 상태의 글로우**. 보드의 기억에 남는 한 가지로 만들고, 나머지는 조용하게.
- **품질 바닥선**: 다크/라이트 테마 지원, `reduced motion` 존중, 작은 화면(폰 세로)에서 깨지지 않게.
- 흔한 AI 기본 룩(크림+세리프+테라코타 / 검정+형광 단색 / 신문조 헤어라인)은 **피한다.**

### 4.5 주스(juice)
- 데미지 시 대상 흔들림 + 데미지 숫자 팝업.
- 미니언 소환 시 등장 애니메이션, 사망 시 페이드/파편.
- 단, 과한 애니메이션은 금물(전부 한 번에 움직이면 AI 생성 느낌). AI 액션 간 `delay`로 리듬을 만든다.

---

## 5. 파일/패키지 구조 (제안)

```
app/src/main/java/com/lsk/cardgame/
├─ domain/
│  ├─ Card.kt            // Card, CardType
│  ├─ Effect.kt          // Effect, Target, Trigger, Ability
│  ├─ Zone.kt
│  ├─ Player.kt
│  ├─ GameState.kt
│  ├─ EventBus.kt        // GameEvent, EventBus
│  ├─ GameAction.kt      // ★ 신규 (3.2)
│  ├─ GameEngine.kt      // apply / legalActions / planAiTurn / startTurn / isGameOver / winner
│  └─ CardPool.kt        // wolf()/bear()/fireball()... + buildDeck()
├─ presentation/
│  ├─ GameViewModel.kt
│  ├─ model/GameUiState.kt   // GameUiState, MinionUi, CardUi, Phase
│  └─ ui/
│     ├─ GameScreen.kt
│     ├─ components/ (HeroBar.kt, MinionView.kt, HandCardView.kt, ManaIndicator.kt, GameLogPanel.kt, GameOverDialog.kt)
│     └─ theme/ (Color.kt, Type.kt, Theme.kt)
└─ MainActivity.kt
```

`domain/` 내부는 `PlayableCardGame.kt`에서 거의 그대로 가져오되, 3장의 리팩터링을 적용해 파일별로 분리한다.

---

## 6. 마일스톤 (순서대로 진행, 각 단계 끝에 빌드/커밋)

1. **프로젝트 골격**: Compose 프로젝트 생성 + 의존성 + 빈 `MainActivity`. 빌드 통과.
2. **도메인 이식**: `PlayableCardGame.kt`를 `domain/`으로 분리 이식. `println→log` 콜백, `readLine` 루프 제거, `GameAction`/`apply`/`legalActions`/`planAiTurn` 도입. **단위 테스트**로 "스크립트 액션 시퀀스 한 판"이 승/패까지 도는지 검증(UI 없이).
3. **ViewModel + 스냅샷**: `GameUiState` 변환 + `StateFlow`. 턴 흐름 오케스트레이션(사람 대기 ↔ AI planTurn 실행). 아직 UI 없음.
4. **정적 화면**: `GameScreen`이 `GameUiState`를 그리기만(상호작용 X). 상태가 올바르게 보이는지 확인.
5. **상호작용 연결**: 손패 탭=PlayCard, 미니언 탭→공격 대상 선택, 턴 종료. 사람 턴 완전 플레이 가능.
6. **AI 연출**: 코루틴 + `delay`로 AI 턴 단계별 진행. 한 판 끝까지 플레이 + `GameOverDialog`.
7. **디자인·주스**: 4.4/4.5 적용, 다크/라이트 테마, 애니메이션. "새 게임" 버튼.

---

## 7. 수용 기준 (Acceptance Criteria)

- [ ] 폰(또는 에뮬레이터)에서 설치·실행, **끝까지 1판(승/패)** 플레이 가능.
- [ ] AI가 자동으로 턴을 진행하고, 진행이 눈으로 보인다(즉시 끝나지 않음).
- [ ] `domain/` 패키지에 `android.*` import **0**. 도메인만으로 단위 테스트 1개 이상 통과.
- [ ] 콘솔 `println`/`readLine` **잔존 0**. 모든 피드백은 UI 로그/애니메이션.
- [ ] 화면 회전 시 진행 상태 유지(ViewModel 사용으로 기본 충족).
- [ ] 마나 부족 카드 비활성, 공격 가능 미니언 하이라이트가 정확히 표시.

---

## 8. 주의사항 (Gotchas)

- **참조 동일성**: `StateFlow`에 같은 인스턴스를 emit하면 Compose가 리컴포즈하지 않는다. 매번 새 `GameUiState`를 만들 것.
- **도메인 순수성**: 편하다고 도메인에 `Context`/`Log`/`Toast`를 넣지 말 것. 로그는 콜백, 그 외는 ViewModel/UI에서.
- **메인스레드 블로킹 금지**: AI 턴을 `viewModelScope`(코루틴)에서 돌리고 `delay` 사용. `Thread.sleep` 금지.
- **규칙 보존**: 턴/마나/소환멀미/반격/탈진/승패는 `PlayableCardGame.kt`와 동일하게 유지. 리팩터는 진입점(GameAction)만 바꾼다.
- **버전 하드코딩 금지**: 1장 참고. 최신 안정 버전 확인 후 적용.
- **대상 지정 주문 제외**: 이번 버전은 콘솔과 동일하게 고정 대상 효과만. 임의 대상 선택은 후속.

---

## 부록 A. 도메인 모델 요약 (원본 미제공 시 참고)

- `Card(name, cost, type: MINION|SPELL, baseAttack, baseHealth, abilities)` + 가변 `attack/health/maxHealth/readyToAttack`, `isDead`, `canAttack`.
- `Effect`(sealed): `DealDamage`, `Heal`, `DrawCards`, `Buff` / `Target`(enum): ENEMY_HERO, OWNER_HERO, SELF, ALL_FRIENDLY_MINIONS, ALL_ENEMY_MINIONS.
- `Trigger`(enum): CAST, BATTLECRY, DEATHRATTLE, ON_FRIENDLY_SUMMON / `Ability(trigger, effect)`.
- `Zone(name)` { cards }, `Player` { heroHealth(30), mana, maxMana, fatigue, deck/hand/field/graveyard }.
- `GameState(player1, player2)` { turn, opponentOf() }.
- `EventBus` { subscribe, publish }, `GameEvent`: MinionSummoned, MinionDied.
- `GameEngine`: 카드 내기·전투·효과 해결·죽음 처리(상태 기반)·드로우(+탈진)·턴 시작·승패. **여기에 3.2의 apply/legalActions/planAiTurn을 추가**.
- 카드 풀: 늑대(1코 2/1), 신병(2코 2/3), 곰(3코 4/5), 거인(6코 6/7), 화염술사(2코 2/2, 전투의함성 적영웅2뎀), 유령(2코 2/2, 죽음의메아리 드로우1), 소집관(4코 3/4, 아군소환시 전아군+1/+1), 화염구(4코 주문 적영웅6뎀), 치유의빛(2코 주문 내영웅6회복), 화염폭풍(5코 주문 적미니언전체3뎀).

## 부록 B. 작업자에게 (Claude Code)
- 코드 변경은 **마일스톤 단위로 커밋**하고, 각 PR에 적용한 버전과 결정 사항을 적을 것.
- UI는 4.4의 시작 제안을 **그대로 베끼지 말고**, 한 번의 디자인 패스로 다듬어 의도를 설명할 것(템플릿 기본값 회피).
- 막히거나 모호하면 가정을 명시하고 진행할 것.
