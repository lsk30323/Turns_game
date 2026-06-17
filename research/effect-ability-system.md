# 효과/능력 시스템 (Effect / Ability System) 리서치

> 대상: 콘솔 Kotlin 카드게임(`PlayableCardGame.kt`, Hearthstone 스타일 학습용 프로토타입)의 Android 포팅.
> 레퍼런스 도메인: **EventBus + Effect(sealed: DealDamage/Heal/DrawCards/Buff) + Trigger(enum: CAST/BATTLECRY/DEATHRATTLE/ON_FRIENDLY_SUMMON) + Ability(trigger, effect)**, GameEvent(MinionSummoned/MinionDied), 순차 해결(no stack/chain).
>
> 본 문서는 효과를 **어떻게 표현하고(represent) · 어떻게 연결하며(connect) · 어떻게 해결하는가(resolve)** 에 집중한다. *언제 발동하는가*(우선순위/스택/타이밍)는 별도 주제이므로 다루지 않는다.

---

## 1. 개요 (Overview)

카드게임의 "능력 시스템"은 본질적으로 **이벤트가 발생했을 때 작은 효과 단위들을 실행하는 발행/구독(pub-sub) 기계**다. 가장 흔한 초보 구현은 카드별 동작을 `when(card.name)` / `if(trigger == ...)` 식의 거대한 분기로 하드코딩하는 것인데, 이는 카드가 늘수록 폭발적으로 복잡해지고 조합·재사용이 불가능하다.

성숙한 엔진(MTG Forge, SabberStone, Fireplace)은 공통적으로 두 가지를 분리한다.

1. **효과(Effect) = "무엇을 한다"** — 데미지/드로우/힐/버프 같은 작고 파라미터화된 실행 단위. 데이터처럼 취급되고 조합·재사용된다.
2. **트리거(Trigger) = "언제 그것을 실행하는가"** — 특정 게임 이벤트(소환됨/죽음/공격함 등)에 효과를 묶는 후크(hook).

레퍼런스 도메인의 `Ability(trigger, effect)`는 정확히 이 분리를 코드로 표현한 것이며, 이는 업계 표준 설계와 직접 대응된다. "생물이 전장에 들어올 때마다 +1/+1" = `Ability(ON_FRIENDLY_SUMMON, Buff(1,1))`처럼 **요리 레시피처럼 조립**되어야 하고, 새 카드 추가가 새 분기문이 아니라 새 데이터 조합이 되어야 한다.

---

## 2. 핵심 패턴 (Core Patterns)

### 2.1 능력의 4가지 분류 (Ability Taxonomy)

MTG/일반 카드게임 규칙에서 능력은 다음으로 나뉜다. 효과 시스템 설계 시 어떤 종류를 지원할지가 첫 결정이다. (출처: MTG Wiki, cardboard docs, Nerdventure/CoolStuffInc)

| 종류 | 정의 | 발동 방식 | 프로토타입 대응 |
|---|---|---|---|
| **Triggered (촉발형)** | "~할 때마다(when/whenever/at)" 이벤트에 반응 | 이벤트 발생 시 자동 | **BATTLECRY, DEATHRATTLE, ON_FRIENDLY_SUMMON** |
| **Activated (기동형)** | 비용:효과 형태, 플레이어가 능동 발동 | 플레이어 입력 | **CAST**(주문 시전)가 근접 |
| **Static (지속형)** | "항상 참"인 상태, 스택에 안 올라감 | 조건 충족 동안 상시 적용 | (현재 없음 — 영구 버프/오라) |
| **Replacement (대체)** | 한 이벤트를 다른 이벤트로 *대체* ("instead") | 이벤트 직전 가로채기 | (현재 없음 — 고급) |

> **프로토타입 권고**: Triggered + (CAST=즉시 효과)만으로 충분하다. Static(오라)/Replacement는 학습용 범위를 넘으며, 무한루프·계층 재계산 등 별도 난제를 끌고 온다. 처음엔 의도적으로 제외하고, 확장 지점만 열어두는 것이 좋다.

### 2.2 효과 = 작은 명령 객체 (Effect as small Command unit)

핵심 패턴은 **Command 패턴**이다. 각 효과를 "무엇을 하는지 캡슐화한 객체"로 만들어 호출자(이벤트 디스패처)와 수신자(게임 상태)를 분리한다. 레퍼런스의 `sealed class Effect`가 바로 이것이며, 다음 이점을 제공한다.

- **재사용**: "카드 1장 드로우" 효과는 배틀크라이든 데스래틀이든 동일 객체. (Fireplace 설계 철학: *"Battlecry of Draw a card와 Deathrattle of Draw a card는 다를 이유가 없다"* — 그래서 `deathrattle = drawCard`, `action = drawCards(2)`처럼 동일 헬퍼 재사용. 출처: Fireplace Card API)
- **조합(composition)**: 여러 Effect를 리스트로 묶어 순차 실행 → 콤보 카드. (SabberStone의 `ComplexTask.Create(taskA, taskB)`)
- **선언적**: 카드 정의가 분기 코드가 아니라 데이터 조립이 됨.

### 2.3 후크-콜백 = 트리거 등록 (Hook-Callback via EventBus)

**Observer 패턴 / EventBus**가 "언제"를 담당한다. 게임 상태 변화를 `GameEvent`(MinionSummoned, MinionDied)로 발행하면, 트리거를 가진 카드들이 콜백으로 반응한다.

```
이벤트 발생 → EventBus 발행 → 등록된 Ability 중 trigger 일치 항목 수집 → 각 effect 순차 resolve
```

핵심은 **콜백을 동적으로 등록/해제**할 수 있다는 점이다. 미니언이 전장에 들어올 때 그 트리거를 EventBus에 구독시키고, 죽거나 침묵(silence)되면 구독 해제한다. 이렇게 하면 "전장에 있는 동안만 반응"이 if문 없이 표현된다. (출처: Game Programming Patterns — Observer; Nomad/Godot EventBus 튜토리얼)

### 2.4 트리거-효과 결합 = 간접 참조 (Trigger → Effect linkage)

실제 엔진은 트리거와 효과를 **데이터 링크**로 연결한다.

- **MTG Forge**: `T:Mode$ ChangesZone | ... | Execute$ TrigDraw` — 트리거 라인의 `Execute$`가 효과를 담은 SVar를 *이름으로* 참조. `Mode$`가 이벤트 종류(트리거 enum과 동일 역할), `Execute$`가 effect 핸들. (출처: Forge Triggers wiki)
- **SabberStone**: `new Trigger(TriggerType.HEAL) { TriggerSource = ALL_MINIONS, SingleTask = new DrawTask() }` — 트리거 타입 + 소스 필터 + 실행할 task. (출처: SabberStone CoreCardsGen)

레퍼런스의 `Ability(trigger, effect)`는 이 둘을 **하나의 데이터 객체로 직접 묶은** 가장 단순·명료한 형태이며, 학습용으로는 Forge의 SVar 간접참조보다 우월하다(인디렉션 불필요).

---

## 3. 코드 구조 (훅 · 콜백 · Effect 단위)

### 3.1 Effect 단위 (sealed + 실행 컨텍스트)

레퍼런스 sealed 구조를 그대로 유지하되, 각 Effect가 **게임 상태 + 타겟 컨텍스트**를 받아 자기 자신을 실행하도록 한다(Command 패턴의 `execute(receiver)`).

```kotlin
sealed interface Effect {
    fun resolve(ctx: EffectContext)   // receiver = 게임상태/이벤트버스
}
data class DealDamage(val amount: Int) : Effect { ... }
data class Heal(val amount: Int) : Effect { ... }
data class DrawCards(val count: Int) : Effect { ... }
data class Buff(val atk: Int, val hp: Int) : Effect { ... }
// 조합: 콤보 = 여러 Effect를 순차 resolve (SabberStone ComplexTask 대응)
data class CompositeEffect(val parts: List<Effect>) : Effect { ... }
```

`EffectContext`에는 source(능력 주인), targets, gameState, eventBus가 들어간다. **타겟팅**은 Effect 자체가 아니라 컨텍스트가 공급한다 — Forge의 `Defined$ You | TargetType$ ...`, SabberStone의 `EntityType.TARGET / ENEMIES_NOTARGET`처럼 "선택자(selector)"를 효과와 분리하면 같은 DealDamage를 단일/광역에 재사용할 수 있다.

> 타겟 선택자 예시: `Self`, `Target(single)`, `AllEnemies`, `AllFriendlyMinions`, `RandomEnemy`. 이를 enum/sealed로 분리하면 효과 수 × 타겟 수 조합 폭발을 곱셈이 아닌 덧셈으로 억제.

### 3.2 트리거 후크 등록

```kotlin
enum class Trigger { CAST, BATTLECRY, DEATHRATTLE, ON_FRIENDLY_SUMMON }
data class Ability(val trigger: Trigger, val effect: Effect)

class EventBus {
    // GameEvent 타입별로 (구독자, Ability) 등록
    fun publish(event: GameEvent) {
        collectMatchingAbilities(event)      // 1) order-of-play 순으로 수집
            .forEach { (owner, ab) -> ab.effect.resolve(ctx(owner, event)) }  // 2) 순차 해결
    }
}
```

- 미니언이 전장 진입 시: 자신의 트리거형 Ability들을 EventBus에 등록(구독).
- `MinionSummoned` 발행 → `ON_FRIENDLY_SUMMON` Ability들이 반응 (= "생물 진입 시 +1/+1" 후크-콜백 예시).
- `BATTLECRY`는 소환 *직전* 자기 효과 즉시 실행(이벤트 구독 불필요, 일회성), `DEATHRATTLE`은 `MinionDied`에 반응.

### 3.3 카드 정의 = 선언적 데이터

목표: 카드 추가 = 데이터 한 줄. (SabberStone CoreCardsGen 패턴과 동일)

```kotlin
// "전투의 함성: 카드 1장 뽑기"
Card("아우누", abilities = listOf(Ability(BATTLECRY, DrawCards(1))))
// "죽음의 메아리: 적 전체 1 피해"
Card("...", abilities = listOf(Ability(DEATHRATTLE, DealDamage(1) /*target=AllEnemies*/)))
// "내 하수인 소환될 때마다 +1/+1"
Card("...", abilities = listOf(Ability(ON_FRIENDLY_SUMMON, Buff(1,1))))
```

이렇게 하면 `when(cardName)` 분기가 사라지고, 게임 로직은 **"이벤트 발행 → 매칭 Ability 수집 → effect.resolve"** 단 하나의 경로만 갖는다. 새 카드는 새 코드가 아니라 새 `Ability` 조합이다.

### 3.4 Command + EventBus 결합 (요약)

| 역할 | 패턴 | 레퍼런스 요소 |
|---|---|---|
| "무엇을 한다" 캡슐화 | Command | `Effect.resolve()` |
| "언제 한다" 발행/구독 | Observer / EventBus | `EventBus`, `GameEvent`, `Trigger` |
| "무엇+언제" 묶음 | (데이터 바인딩) | `Ability(trigger, effect)` |
| 효과 조합 | Composite | `CompositeEffect(list)` |
| 타겟 분리 | Strategy/Selector | `EffectContext.targets` |

---

## 4. 실제 게임 비교 (Real-Engine Comparison)

### 4.1 MTG Forge (Java, 텍스트 스크립트 DSL)
- 능력 3종 접두사: `T:`(triggered) `A:`(activated) `S:`(static). 효과는 `SP$ Draw | Defined$ You | NumCards$ 1`처럼 **파라미터화된 선언**.
- `AbilityFactory`가 스크립트를 읽어 `SpellAbility` 객체를 생성(팩토리 패턴) — Java 코딩 없이 카드 추가.
- 트리거는 `Mode$`(ChangesZone, Attacks, DamageDone, SpellCast, Sacrificed, Drawn 등 풍부한 taxonomy) + `Execute$ SVar`로 효과 연결. `SVar`는 재사용 가능한 계산/효과 변수.
- **시사점**: 프로토타입의 4개 Trigger enum은 Forge `Mode$`의 축소판. 다만 Forge는 텍스트 DSL+SVar 간접참조라 학습용엔 과함. Kotlin sealed class가 타입 안전성 면에서 더 적합.

### 4.2 SabberStone (C#, 선언적 Task 시스템) — **가장 가까운 모델**
- 카드 = `CardDef(playReqs, Power{ ... })`. `Power`에 `PowerTask`(=배틀크라이), `DeathrattleTask`, `Trigger`를 담음.
- 효과 = **Task 객체**: `DamageTask`, `DrawTask`, `SummonTask`, `HealTask`. 조합은 `ComplexTask.Create(taskA, taskB)`.
- 트리거 = `new Trigger(TriggerType.HEAL){ TriggerSource = ALL_MINIONS, SingleTask = new DrawTask() }`.
- 오라/인챈트는 별도 "onion(계층)" 시스템으로 처리(static ability에 해당, 복잡도 높음).
- **시사점**: 레퍼런스 도메인과 거의 1:1 대응. `Effect ≈ Task`, `Ability(trigger,effect) ≈ Trigger{type, SingleTask}`, `CompositeEffect ≈ ComplexTask`. 이 프로토타입은 **SabberStone의 축소판을 Kotlin sealed class로 구현하는 것**이라고 보면 정확하다.

### 4.3 Fireplace (Python, 통합 액션 엔진)
- `action`(배틀크라이/주문), `deathrattle`, `combo`, `inspire`를 카드 클래스 속성으로 선언. 값은 "Action들의 iterable 또는 callable".
- 같은 효과 헬퍼를 트리거 종류와 무관하게 재사용(`deathrattle = drawCard`).
- `events` 속성 = `EventListener` 목록, Action에 `on()/after()/once()`를 호출해 생성 → 반응형 체인.
- **시사점**: "효과는 트리거와 독립적인 단위"라는 철학을 가장 명확히 보여줌. 레퍼런스가 effect를 trigger와 분리한 것이 옳다는 근거.

### 4.4 Hearthstone 실제 동작(규칙) — 해결 모델 참고
- 이벤트/트리거는 **order of play(전장 진입 순서, 오래된→최신)**로 Queue에 담겨 순차 해결. MTG의 APNAP과 다름.
- Queue는 첫 항목 해결 시작 시 **불변(immutable)** — 해결 중 새 트리거는 그 큐에 추가되지 않음.
- 죽음 체크는 **이벤트 페이즈 종료 시점**에 한꺼번에(0 이하 체력 미니언 파괴).
- **시사점**: 프로토타입이 "순차 해결, no stack"인 것은 Hearthstone의 실제 단순화 모델과 일치. 단, "한 이벤트 처리 중 발생한 새 이벤트"를 어떻게 큐잉하는지는 명시 필요(§5 참고).

---

## 5. 흔한 함정 (Common Pitfalls)

1. **무한 루프 (Infinite trigger loops)**
   - 예: "내 하수인 죽을 때마다 하수인 소환" + "하수인 소환될 때마다 1 피해" → 서로 물고 무한 반복.
   - Hearthstone 방어책: 해결 중 큐 불변 + "해결 도중 소환된 엔티티는 현재 해결 중인 이벤트에 반응 못 함". (출처: Advanced rulebook)
   - Game Programming Patterns(Event Queue)도 "이벤트 처리가 또 이벤트를 enqueue하는 피드백 루프"를 명시적 경고.
   - **구현**: 효과를 즉시 재귀 실행하지 말고 **이벤트 큐(FIFO)에 적재 후 디스패치 루프에서 처리**. 깊이/반복 상한(루프 가드)을 둔다.

2. **순차 재귀 vs 큐잉 모호성 (Re-entrancy)**
   - `effect.resolve()` 안에서 또 `eventBus.publish()`를 호출하면 콜스택이 깊어지고 순서가 뒤엉킴. EventBus는 "발행 → 큐 적재 → 현재 디스패치 끝난 뒤 처리" 또는 "현재 배치 수집을 동결한 뒤 새 이벤트는 다음 배치로"를 명확히 정해야 한다.

3. **트리거 수집 시점과 상태 변화 (Snapshot vs live)**
   - 효과 해결 중 미니언이 죽으면 그 트리거를 실행해야 하나? Hearthstone은 "트리거 목록을 order-of-play로 스냅샷한 뒤 그 목록을 순회". 순회 중 컬렉션 변경(ConcurrentModification)을 피하려면 **수집(snapshot) → 실행** 2단계로 분리.

4. **효과 상호의존 / 순서 모호성 (Effect interdependency & ordering)**
   - 동시에 발동하는 여러 트리거의 순서. 단일 플레이어 프로토타입이면 단순 order-of-play로 충분하나, 규칙을 **명시적으로 한 곳(수집 정렬 로직)에** 둔다. if/else에 흩뿌리면 디버깅 불가.

5. **하드코딩 회귀 (if/else 재발)**
   - "이 카드만 특별 처리" 유혹 → 분기 추가. 대신 **새 Effect 서브타입 또는 새 타겟 셀렉터**를 추가하는 방향을 강제. sealed class의 `when`은 컴파일러가 누락을 잡아주므로(exhaustive) 안전한 분기.

6. **콜백 생명주기 (Lapsed-listener)**
   - 죽은/침묵된 미니언의 트리거를 EventBus에서 **해제하지 않으면** 유령 콜백이 계속 발동하거나 메모리 누수. 진입 시 구독 / 퇴장 시 해제를 짝지어 관리. (출처: Observer 패턴 — lapsed listener 문제)

7. **타겟 무효화 (Fizzle)**
   - 효과 해결 시점에 타겟이 이미 죽었으면? null-safe하게 "타겟 없으면 그 효과는 무시(fizzle)"를 기본 정책으로. 각 Effect가 방어적으로 처리.

8. **Static/Aura 도입 시 재계산 폭발**
   - "+1/+1 오라"를 Buff 이벤트로 구현하면 영구 누적되어 버그. Static은 "이벤트로 한 번 바꾸는 것"이 아니라 "조건 동안 매번 재계산하는 계층(layer)"이라 근본적으로 다름(SabberStone onion 시스템). **프로토타입에서는 도입을 미루는 것이 안전.**

---

## 6. 권고 사항 (Recommendations — 이 프로토타입 적합성)

- **현 설계는 적합하다.** `EventBus + sealed Effect + Trigger enum + Ability(trigger, effect)` 조합은 SabberStone(축소판)·Fireplace의 검증된 구조와 직접 대응하며, Command(효과) + Observer(트리거) 결합의 교과서적 형태다.
- **추가로 권하는 4가지:**
  1. `CompositeEffect`(효과 리스트) 추가 → 콤보/다단 효과를 if 없이. (= SabberStone ComplexTask)
  2. **타겟 셀렉터를 Effect에서 분리**(sealed `TargetSelector`) → 효과 재사용성 극대화, 조합 폭발 억제.
  3. **이벤트는 즉시 재귀가 아니라 FIFO 큐로 디스패치** → 무한루프/순서 문제 원천 차단. 루프 가드(상한) 포함.
  4. **트리거 처리는 snapshot → resolve 2단계** + 진입/퇴장 시 구독/해제 짝맞춤.
- **의도적으로 제외할 것(학습용 범위 보호):** Static/Aura 계층, Replacement 효과, 스택/우선순위 체인. 확장 포인트(예: `Trigger`에 항목 추가, `Effect` 서브타입 추가)만 열어두면 충분.
- 한 줄 요약: **"카드 = 데이터(Ability 조합), 로직 = 이벤트 발행→매칭 수집→순차 resolve 단일 경로"** 를 끝까지 지키면 if/else 하드코딩 없이 확장 가능한 효과 시스템이 된다.

---

## 7. 출처 URL 목록 (Sources)

**능력 분류 / 정의**
- Triggered ability — MTG Wiki: https://mtg.fandom.com/wiki/Triggered_ability
- Replacement effect — MTG Wiki: https://mtg.fandom.com/wiki/Replacement_effect
- Spells, Abilities, and Effects — cardboard docs: https://cardboard.readthedocs.io/en/latest/rules/spells_abilities_and_effects.html
- Activated/Triggered/Static/Mana abilities — Nerdventure: https://nerdventure.com/magic-basics-mtg-whats-the-difference-between-activated-triggered-static-and-mana-abilities/
- Difference between ability types — CoolStuffInc: https://www.coolstuffinc.com/a/markwischkaemper-seo-06112024-whats-the-difference-between-activated-triggered-and-static-abilities

**MTG Forge 엔진 (스크립트 DSL / AbilityFactory / Triggers)**
- Card scripting API — Card-Forge/forge Wiki: https://github.com/Card-Forge/forge/wiki/Card-scripting-API
- Triggers — Card-Forge/forge Wiki: https://github.com/Card-Forge/forge/wiki/Triggers
- Creating a custom Card — Card-Forge/forge Wiki: https://github.com/Card-Forge/forge/wiki/Creating-a-custom-Card
- Forge repo: https://github.com/Card-Forge/forge

**SabberStone (선언적 Task 시스템) — 가장 근접 모델**
- SabberStone repo (HearthSim): https://github.com/HearthSim/SabberStone
- CoreCardsGen.cs (카드 정의 예시): https://github.com/HearthSim/SabberStone/blob/master/SabberStoneCore/src/CardSets/Standard/CoreCardsGen.cs

**Fireplace (통합 액션 엔진)**
- The Fireplace Card API: https://github.com/jleclanche/fireplace/wiki/1:-The-Fireplace-Card-API
- Fireplace repo: https://github.com/jleclanche/fireplace
- HearthSim simulators 목록: https://hearthsim.info/simulators/

**Hearthstone 실제 해결 모델 / 함정**
- Advanced rulebook — Hearthstone Wiki (order of play, queue immutability, 무한루프 방어): https://hearthstone.wiki.gg/wiki/Advanced_rulebook
- Triggered effect — Hearthstone Wiki: https://hearthstone.wiki.gg/wiki/Triggered_effect
- Deathrattle — Hearthstone Wiki: https://hearthstone.fandom.com/wiki/Deathrattle

**설계 패턴 (Command / Observer / Event Queue / EventBus)**
- Command — Game Programming Patterns: https://gameprogrammingpatterns.com/command.html
- Observer — Game Programming Patterns: https://gameprogrammingpatterns.com/observer.html
- Event Queue — Game Programming Patterns: https://gameprogrammingpatterns.com/event-queue.html
- Nomad Game Engine — The Event System: https://medium.com/@savas/nomad-game-engine-part-7-the-event-system-45a809ccb68f
- The Events bus singleton — GDQuest: https://www.gdquest.com/tutorial/godot/design-patterns/event-bus-singleton/

**무한루프 / 동시 트리거 순서**
- MTG APNAP 가이드 — TCG Protectors: https://tcgprotectors.com/blogs/magic-the-gathering-blog/mtg-guide-stacking-triggers
- Infinite loop — Wikipedia: https://en.wikipedia.org/wiki/Infinite_loop
