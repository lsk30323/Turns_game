# 카드 = 데이터 + 효과 스크립트 (Card as Data + Effect Script)

> 리서치 대상: 콘솔용 Kotlin 학습 프로토타입(`PlayableCardGame.kt`, Hearthstone 풍)을 Android로 포팅.
> 초점: **"카드를 어떻게 데이터로 표현하고 로드하는가"** — 효과 해소(resolution) 메커니즘은 별도 주제.

---

## 1. 개요 (Overview)

턴제 카드 게임에서 "카드 = 데이터 + 효과 스크립트"는 **카드의 정적 정의(이름·코스트·스탯·능력)를 코드가 아닌 데이터로 분리**하고, 그 데이터가 가리키는 **효과 동작은 엔진 코드(또는 스크립트)가 해석**하도록 만드는 설계 원칙이다. 핵심 목표는 다음 두 가지다.

1. **데이터/로직 경계(data/logic boundary)**: 카드를 추가/수정할 때 게임 규칙 코드(턴, 우선순위, 대상 선택 등)를 건드리지 않는다.
2. **확장(expansion) 용이성**: 새 카드 세트를 데이터 파일 추가만으로 출시할 수 있다.

현재 프로토타입은 이미 이 방향에 근접해 있다. `CardPool`의 팩토리 함수, `sealed` 효과 타입(`DealDamage`/`Heal`/`DrawCards`/`Buff`), `Target`/`Trigger` enum, `Ability(trigger, effect)` 구조는 사실상 **코드 내장(in-code) 데이터 정의**다. 이 리서치는 이 구조를 (a) 그대로 유지할지, (b) JSON 등 외부 데이터로 끌어낼지 판단하기 위한 비교 자료다.

---

## 2. 핵심 패턴 (Core Patterns)

### 2.1 템플릿(정의) vs 인스턴스 분리

가장 중요한 개념. 두 개의 서로 다른 타입이 필요하다.

| 구분 | 역할 | 가변성 | 예 |
|------|------|--------|-----|
| **CardDefinition (template)** | 카드의 불변 청사진. "곰은 3코스트 4/5" | 불변, 게임 내내 공유 | `CardData` 1개 |
| **CardInstance (instance)** | 실제 판 위의 한 장. 현재 체력, 받은 버프, 소유자, 도발/은신 상태 | 가변, 카드마다 독립 | 같은 곰이라도 인스턴스 N개 |

- 인스턴스는 정의를 **참조(id 또는 포인터)** 로만 들고, 변하는 상태만 자체 보유한다.
- 이 분리를 안 하면 "버프 받은 곰" 하나가 다른 모든 곰의 스탯을 오염시키는 고전적 버그가 난다.
- 거의 모든 상용 엔진이 이 분리를 강제한다(Hearthstone의 entity vs card definition, TCG Engine의 `CardData`(SO) vs `Card`(런타임)).

### 2.2 효과를 데이터로 표현하는 3가지 형태

세 형태는 "표현력 ↔ 단순함" 스펙트럼 위에 있다.

1. **태그/열거형 + 엔진 구현 (Tag-driven)** — *Hearthstone*
   - 데이터에는 `mechanics: ["BATTLECRY", "DEATHRATTLE"]` 같은 **마커 태그**만. 실제 동작은 엔진 코드(혹은 카드 id별 스크립트)에 하드코딩.
   - 장점: 데이터가 가볍고 검증 쉬움. 단점: 새 효과 = 코드 작성 필요. 데이터만으로 신규 효과 생성 불가.

2. **파라미터화된 효과 DSL (Parameterized effect vocabulary)** — *MTG Forge, TCG Engine*
   - **고정된 효과 어휘**(`DealDamage`, `Draw`, `Buff`…)를 엔진이 구현하고, 카드 데이터는 그 어휘를 **파라미터와 함께 조합**한다.
   - Forge 예: `A:SP$ DealDamage | Cost$ R | Tgt$ TgtCP | NumDmg$ 2`
   - 새 카드 = 데이터 조합만으로 가능(무코딩). 새 *효과 종류* = 엔진에 새 클래스 추가 필요.
   - **이 프로토타입의 sealed-effect 모델이 정확히 이 범주.**

3. **임베디드 스크립트 (Embedded scripting)** — *YGOPro/EDOPro (Lua)*
   - 카드마다 튜링 완전 스크립트(`c12345.lua`)를 두고, 스탯은 별도 DB(`cards.cdb`, SQLite)에 저장. 효과 로직과 데이터가 **물리적으로 분리**되어 id(passcode)로 연결.
   - 장점: 임의의 복잡한 효과를 코드 재컴파일 없이 추가. 단점: 인터프리터 내장·샌드박싱·디버깅 부담, 학습 프로토타입엔 과함.

### 2.3 데이터 저장 위치: 코드 / JSON / DB

- **코드 내장(factory/DSL)**: 컴파일 타임 타입 안전, IDE 자동완성, 검증 불필요. 단점: 카드 추가 시 재빌드, 비개발자 편집 불가.
- **JSON/XML 외부 파일**: 재빌드 없이 확장, 비개발자·툴 편집 가능. 단점: 런타임 파싱·검증·역직렬화 오류 처리 필요.
- **DB row(SQLite)**: 대량 카드·쿼리·로컬라이징에 유리(Hearthstone, YGOPro의 cdb). 단점: 스키마 관리 오버헤드.

---

## 3. 데이터 스키마·코드 형태 (Schema & Code Shape)

### 3.1 카드 정의 스키마 (공통 필드)

거의 모든 엔진이 수렴하는 최소 필드:

```
id            안정적 고유 키 (절대 변경 금지 — 세이브/덱이 이 키를 참조)
name, text    표시용 (로컬라이징은 보통 키→문자열 테이블로 분리)
type          MINION / SPELL (/ HERO, WEAPON …)
cost          마나 코스트
attack, health 미니언 스탯
abilities[]   능력 목록 (trigger + effect)
set, rarity   확장/희귀도 (선택)
collectible   수집 가능 여부 (선택)
```

### 3.2 능력/효과 스키마 (이 프로토타입에 맞춘 형태)

현재 sealed 구조를 JSON으로 외부화할 경우의 권장 형태. kotlinx.serialization의 **discriminated union**(`type` 판별자)을 사용:

```json
{
  "id": "pyromancer",
  "name": "Pyromancer",
  "type": "MINION",
  "cost": 2,
  "attack": 2,
  "health": 2,
  "abilities": [
    {
      "trigger": "BATTLECRY",
      "effect": { "type": "DealDamage", "amount": 2, "target": "ENEMY_HERO" }
    }
  ]
}
```

대응 Kotlin:

```kotlin
@Serializable
sealed interface Effect

@Serializable @SerialName("DealDamage")
data class DealDamage(val amount: Int, val target: Target) : Effect

@Serializable @SerialName("Heal")
data class Heal(val amount: Int, val target: Target) : Effect

@Serializable @SerialName("DrawCards")
data class DrawCards(val count: Int) : Effect

@Serializable @SerialName("Buff")
data class Buff(val attack: Int, val health: Int, val target: Target) : Effect

@Serializable enum class Target { ENEMY_HERO, OWN_HERO, ALL_ENEMY_MINIONS, ALL_FRIENDLY /* … */ }
@Serializable enum class Trigger { CAST, BATTLECRY, DEATHRATTLE, ON_FRIENDLY_SUMMON }

@Serializable data class Ability(val trigger: Trigger, val effect: Effect)
@Serializable data class CardDef(
    val id: String, val name: String, val type: CardType,
    val cost: Int, val attack: Int = 0, val health: Int = 0,
    val abilities: List<Ability> = emptyList()
)
```

- 직렬화 시 `effect` 객체에 `"type"` 판별자 키가 자동 생성됨(`@SerialName`으로 안정 식별자 지정).
- **closed polymorphism**(sealed)이면 `SerializersModule` 등록 불필요 — 가장 단순. 플러그인식 확장이 필요할 때만 open polymorphism + `SerializersModule`.

### 3.3 인스턴스 형태

```kotlin
class CardInstance(
    val def: CardDef,          // 템플릿 참조 (불변 공유)
    var currentHealth: Int = def.health,
    var attackBuff: Int = 0,
    val owner: Player,
    // 도발/은신/방금 소환됨 등 가변 상태
)
```

### 3.4 실제 게임 데이터 형태 예시

- **MTG Forge** (`res/cardsfolder/*.txt`, 파이프 구분 DSL):
  ```
  Name:Shock
  ManaCost:R
  Types:Instant
  A:SP$ DealDamage | Cost$ R | Tgt$ TgtCP | NumDmg$ 2 | SpellDescription$ ...
  Oracle:Shock deals 2 damage to any target.
  ```
  `K:`(키워드), `A:`(액티브), `T:`(트리거), `S:`(스태틱), `R:`(대체효과), `SVar:`(변수) 접두사로 효과 종류 구분. 효과 어휘는 Java로 구현됨.

- **HearthstoneJSON** (`cards.json`): `id`, `dbfId`, `name`, `cost`, `attack`, `health`, `type`, `text`, `mechanics`(GameTag enum 문자열 배열), `referencedTags`, `set`, `rarity`, `cardClass`, `race`, `collectible`, `playRequirements`(타게팅 제약), `entourage`. **효과 로직은 JSON에 없음** — 태그만 있고 동작은 엔진 코드 담당.

- **TCG Engine** (Unity ScriptableObject): `CardData`(SO, 불변 스탯) + `AbilityData`(SO: `trigger` enum, `conditions_trigger[]`, `effects[]`, `target`, `value`). `EffectData.DoEffect()`, `ConditionData.IsTriggerConditionMet()`를 C# 서브클래스로 구현. **새 카드 = 우클릭 Create로 데이터만; 새 효과 = 새 C# 클래스.**

---

## 4. 실제 게임 비교 (Comparison)

| 엔진 | 데이터 저장 | 효과 표현 | 신규 카드 | 신규 효과 종류 | 표현력 | 프로토타입 적합성 |
|------|------------|-----------|-----------|----------------|--------|------------------|
| **Hearthstone / HearthstoneJSON** | JSON + 내부 DB | 태그(mechanics)만, 동작은 엔진 | 데이터+엔진 | 코드 | 중 | 참고용(스키마 필드 차용) |
| **MTG Forge** | 텍스트 파일 DSL | 파라미터화 효과 어휘 | **무코딩(데이터)** | Java 클래스 | 높음 | ★ 모델 직접 참고 |
| **YGOPro / EDOPro** | 스탯=SQLite cdb, 효과=Lua/카드 | 임베디드 Lua 스크립트 | Lua 스크립트 | Lua | 매우 높음 | 과함 |
| **TCG Engine (indiemarc)** | ScriptableObject | 파라미터화 Effect/Condition SO | **무코딩(SO)** | C# 클래스 | 높음 | ★ 구조 매우 유사 |
| **현재 Kotlin 프로토타입** | 코드(factory) | sealed Effect + Target/Trigger enum | 코드(현재) | sealed 클래스 | 중 | — |

핵심 관찰: **MTG Forge와 TCG Engine 모두 "고정 효과 어휘 + 파라미터" 모델**이며, 이는 현재 프로토타입의 sealed-effect 구조와 동형(isomorphic)이다. 즉 프로토타입은 이미 업계 표준의 "중간 수준(parameterized DSL)" 패턴 위에 있다 — 단지 데이터가 아직 코드 안에 있을 뿐.

---

## 5. 흔한 함정 (Common Pitfalls)

1. **데이터/로직 경계 누수**: 효과 데이터 안에 게임 규칙(턴 순서, 우선순위)을 끼워 넣으면 확장이 깨진다. 데이터는 "무엇을(what)"만, 엔진은 "어떻게(how)"만.
2. **템플릿/인스턴스 미분리**: 정의 객체를 직접 변경(체력 깎기, 버프)하면 같은 카드 전체가 오염된다. 인스턴스에만 가변 상태를 둘 것.
3. **id 불안정성**: id를 이름·인덱스로 쓰면 카드명 변경/세트 추가 시 세이브·덱 데이터가 깨진다. 안정적·불변 문자열 id 필수(Hearthstone, TCG Engine 모두 강조).
4. **다형 직렬화 함정 (Kotlin)**:
   - 모든 서브클래스에 `@Serializable` 필수, sealed가 아니면 `SerializersModule` 등록 누락 시 역직렬화 실패.
   - 컴파일 타임 타입과 런타임 타입 불일치 시 판별자(`type`)가 누락될 수 있음 — 항상 **베이스 타입(`Effect`)으로** 직렬화.
   - `@SerialName` 중복 금지(판별자 충돌).
   - 기본 판별자 키 이름(`type`)이 효과 자체 필드와 충돌하지 않게 주의(`classDiscriminator` 커스터마이즈 가능).
5. **검증 부재**: 외부 JSON으로 가면 잘못된 enum 값·음수 코스트·존재하지 않는 effect type이 런타임에 터진다. 로드시 스키마 검증(필수 필드, enum 범위, 참조 무결성) 단계를 둘 것. 코드 내장 방식은 이 문제가 컴파일 타임에 사라지는 게 최대 장점.
6. **너무 이른 일반화(over-engineering)**: 학습 프로토타입에 Lua 인터프리터·완전한 DSL 파서를 넣으면 핵심(게임 규칙 학습)을 흐린다.

---

## 6. 프로토타입 적합성 및 권장사항 (요약)

- 현재 sealed-effect + enum 구조는 이미 MTG Forge/TCG Engine과 **동일한 "파라미터화 효과 어휘" 패턴**이다. 잘 잡혀 있음.
- **권장(단기)**: 데이터를 **코드 내장 그대로 유지**하되, 카드 정의(`CardDef`)와 런타임 카드(`CardInstance`)를 **명확히 두 타입으로 분리**. 이것이 가장 큰 실질 이득이고 Android 포팅과 무관하게 옳다.
- **권장(중기, 확장 대비)**: sealed 계층에 `@Serializable` + `@SerialName`을 달아 **JSON 직렬화 가능 상태로 만들어 두기**. 당장 외부 파일로 빼지 않더라도, 나중에 `assets/cards.json`으로 옮길 때 코드 변경이 최소화된다. closed polymorphism이라 `SerializersModule`도 불필요.
- **피할 것**: 임베디드 스크립트(Lua) — 이 프로토타입 규모엔 과하다. 새 효과가 sealed 클래스 추가로 충분하다.
- **id 규약**: `"pyromancer"`처럼 안정적 소문자 문자열 id 도입(인덱스/이름 사용 금지).

---

## 7. 출처 URL 목록 (Sources)

- HearthstoneJSON — Cards 문서: https://hearthstonejson.com/docs/cards.html
- HearthstoneJSON.com docs/cards.md (GitHub): https://github.com/HearthSim/HearthstoneJSON.com/blob/master/docs/cards.md
- HearthstoneJSON API 홈: https://hearthstonejson.com/
- HearthSim 카드 데이터 문서: https://hearthsim.info/docs/cards/
- MTG Forge — Card scripting API (Wiki): https://github.com/Card-Forge/forge/wiki/Card-scripting-API
- MTG Forge — Creating a custom Card: https://github.com/Card-Forge/forge/wiki/Creating-a-custom-Card
- MTG Forge — Creating a custom Set: https://github.com/Card-Forge/forge/wiki/Creating-a-custom-set
- Forge & XMage 개요(데이터 주도 룰 엔진): https://cgomesu.com/blog/forge-xmage-mtg/
- TCG Engine — Conditions and Effects: https://indiemarc.gitbook.io/tcg-engine/scripts/conditions-and-effects
- TCG Engine — Cards (CardData): https://indiemarc.gitbook.io/tcg-engine/files-and-data/cards
- TCG Engine — Scripts Overview: https://indiemarc.gitbook.io/tcg-engine/scripts/scripts-overview
- TCG Engine — Resources and Prefabs: https://indiemarc.gitbook.io/tcg-engine/files-and-data/resources-and-prefabs
- YGOPro/EDOPro — Structure of a card script: https://ygoproscripting.miraheze.org/wiki/Structure_of_a_card_script
- Project Ignis CardScripts (EDOPro Lua): https://github.com/ProjectIgnis/CardScripts
- kotlinx.serialization — Polymorphism 문서: https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md
- kotlinx.serialization — JsonContentPolymorphicSerializer: https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json-content-polymorphic-serializer/
- Kotlin Serialization 다형성 실수 사례(Medium): https://medium.com/@kerry.bisset/kotlin-serialization-json-mistakes-i-made-with-polymorphism-and-more-e8ae367dc90a
- Card game model 아키텍처(Benny's Mind Hack): https://bennycheung.github.io/game-architecture-card-ai-1
- Make a CCG – JSON (The Liquid Fire): https://theliquidfire.com/2018/02/19/make-a-ccg-json/
- Designing a Card Game (Batiste, Medium): https://batiste.medium.com/designing-a-card-game-5f610a1fcc71
