# Zone System 리서치 (Hearthstone 스타일 Kotlin 프로토타입 관점)

> 대상: `PlayableCardGame.kt` (콘솔 Kotlin, Hearthstone 스타일 학습용 프로토타입)을 Android 앱으로 이식.
> 도메인 모델: `Zone(name){cards: MutableList<Card>}`, `Player`, `Card(MINION|SPELL ...)`, `GameState`, **상호작용 스택 없음**, 순차 해결, 필드 7장 상한.
> 본 리서치는 모든 항목을 "Hearthstone 스타일 Kotlin 구현과 비교 가능한가"의 기준으로 정리한다.

---

## 개요

**Zone(존)** 은 카드 객체가 게임 중 위치할 수 있는 "장소"이다. MTG 종합 규칙은 존을 "게임 중 객체가 있을 수 있는 장소"로 정의하며, 통상 7개의 존(library, hand, battlefield, graveyard, stack, exile, command)이 존재한다고 명시한다 ([cardboard zones.rst](https://github.com/Julian/cardboard/blob/master/docs/rules/zones.rst), [MTG Wiki - Zone](https://mtg.fandom.com/wiki/Zone)).

Hearthstone은 각 플레이어가 **Deck / Hand / Play(=field/battlefield) / Graveyard** 존을 추적하며, 카드 엔티티는 내부적으로 `PLAY`, `DECK`, `HAND`, `GRAVEYARD`, `REMOVEDFROMGAME`, `SETASIDE`, `SECRET` 같은 Zone 지정값을 가진다 ([Hearthstone Battlefield](https://hearthstone.fandom.com/wiki/Battlefield), [Advanced rulebook 검색결과](https://hearthstone.wiki.gg/wiki/User:Xinhuan/Advanced_rulebook_(rewrite))). 이는 본 프로토타입의 `deck/hand/field/graveyard` 존 모델과 거의 1:1로 대응한다.

핵심 통찰: **존은 단순히 "카드의 리스트"가 아니라, (1) 가시성(public/hidden), (2) 순서(ordered/unordered), (3) 소유(per-player/shared), (4) 용량 한계, (5) 진입/이탈 시 트리거되는 이벤트** 라는 5개 속성을 갖는 자료구조다. 이 프로토타입은 이 중 일부만 필요로 한다(아래 권장사항 참고).

---

## 핵심 패턴

### 1. 존 타입 분류

| 존 | 본 프로토타입 | Hearthstone | MTG | 비고 |
|----|------|------------|-----|------|
| Deck (library) | O | DECK | Library | 숨김, 순서 있음, 플레이어별 |
| Hand | O | HAND | Hand | 숨김, 순서 무관(논리상), 플레이어별 |
| Field (play/battlefield) | O | PLAY | Battlefield | 공개, 위치 의미 있음, HS는 플레이어별 |
| Graveyard | O | GRAVEYARD | Graveyard | (HS 실제론 비활성, MTG는 공개·순서있음) |
| Secret | X(범위 외) | SECRET | (없음) | 숨김, 5장 상한 |
| Exile / Removed | X(범위 외) | REMOVEDFROMGAME | Exile | "게임에서 제거" |
| Stack | **X (의도적 제외)** | (없음) | Stack | 상호작용 체인 — 본 게임은 순차 해결 |
| SetAside | X | SETASIDE | (임시 보관) | 엔진 내부용 |

출처: [MTG Wiki - Zone](https://mtg.fandom.com/wiki/Zone), [Hearthstone Battlefield](https://hearthstone.fandom.com/wiki/Battlefield), [cardboard zones.rst](https://github.com/Julian/cardboard/blob/master/docs/rules/zones.rst).

### 2. 가시성(Visibility): 공개 vs 숨김

- **공개 존(public)**: 모든 플레이어가 카드 면을 볼 수 있다. MTG에서는 graveyard, battlefield, stack, exile, command가 공개 존이다 ([cardboard zones.rst](https://github.com/Julian/cardboard/blob/master/docs/rules/zones.rst), [Draftsim - MTG Zones](https://draftsim.com/zones-mtg/)).
- **숨김 존(hidden)**: 일부/전체 플레이어가 면을 볼 수 없다. library와 hand가 숨김 존이며, 설사 그 안의 카드가 전부 공개되더라도 존 자체는 여전히 hidden으로 분류된다 ([cardboard zones.rst](https://github.com/Julian/cardboard/blob/master/docs/rules/zones.rst)).
- Hearthstone의 **Battlefield는 양 플레이어에게 완전 공개**이나, **Secret만은 예외**로 컨트롤하는 플레이어만 확인 가능한 "뒷면 카드" 역할이다 ([Hearthstone Battlefield](https://hearthstone.fandom.com/wiki/Battlefield)).

### 3. 순서(Ordering)

- MTG에서 **library, graveyard, stack은 "ordered"** 존이며, 효과가 허용하지 않는 한 객체 순서를 바꿀 수 없다 ([MTG Wiki - Zone](https://mtg.fandom.com/wiki/Zone), [cardboard zones.rst](https://github.com/Julian/cardboard/blob/master/docs/rules/zones.rst)).
- 덱은 **top/bottom 개념**이 있어, 드로우는 맨 위에서, 특정 효과는 맨 아래/특정 위치에 넣는다. Forge의 `DigEffect`는 `LibraryPosition` 파라미터로 라이브러리 삽입 위치를 지정한다(검색결과: [Card-Forge/forge code search](https://github.com/Card-Forge/forge)).
- **Field 위치 순서는 게임플레이에 의미가 있다**: Hearthstone에서 미니언은 소환 시 좌/우 위치를 선택하며, 소환 후엔 이동 불가하고, 양 플레이어 화면에서 좌우 배치가 보존된다. 좌측 미니언이 먼저 공격하고 인접(adjacency) 기반 효과가 존재한다 ([Hearthstone Battlefield](https://hearthstone.fandom.com/wiki/Battlefield), [Positional effect](https://hearthstone.fandom.com/wiki/Positional_effect)).
- 따라서 **존을 인덱스 순서가 있는 `List`로 표현하는 것은 정확**하다(프로토타입의 `MutableList<Card>`와 일치).

### 4. 존 변경 이벤트 / 트리거 (Zone-change triggers)

- MTG: 퍼머넌트가 battlefield를 떠나 hand/library/graveyard/exile/command 등 다른 존으로 가는 것은 모두 "leaves the battlefield(LTB)"로 카운트되어 LTB 트리거를 발동한다 ([MTG Wiki - Leaves the battlefield](https://mtg.fandom.com/wiki/Leaves_the_battlefield)). 진입 시에는 ETB(enters the battlefield) 트리거가 발동한다 ([Knockoutsf - ETB Triggers](https://knockoutsf.com/whenever-a-creature-enters-the-battlefield-the-complete-guide-to-etb-triggers-in-magic-the-gathering/)).
- Hearthstone 대응: **Battlecry**(hand→play 진입), **Deathrattle**(play→graveyard 이탈) 가 존-변경 트리거에 해당한다 ([Deathrattle](https://hearthstone.fandom.com/wiki/Deathrattle)).
- 즉, "존 이동 = 이벤트 발생 지점"이라는 패턴이 보편적이다. 본 프로토타입에서 `abilities`(예: 배틀크라이/데스래틀)는 카드가 hand→field, field→graveyard로 이동하는 순간 실행하는 훅으로 모델링하는 것이 자연스럽다.

### 5. 객체 정체성(Object identity) 변경

- MTG 핵심 규칙: **"한 존에서 다른 존으로 이동한 객체는 이전 존재에 대한 기억이 없는 새로운 객체가 된다"** ([cardboard zones.rst](https://github.com/Julian/cardboard/blob/master/docs/rules/zones.rst)). battlefield에 들어오는 퍼머넌트는 같은 카드가 나타낸 이전 퍼머넌트와 아무 관계가 없다 ([검색결과: MTG ETB rules](https://mtg.fandom.com/wiki/Leaves_the_battlefield)).
- 실무적 의미: **존을 떠날 때 카드의 일시적 상태(버프, 데미지, readyToAttack 등)는 리셋**되어야 한다. 본 프로토타입의 `Card`는 가변 `attack/health/maxHealth/readyToAttack`를 가지므로, field→graveyard 또는 hand→field 이동 시 이 임시값을 baseAttack/baseHealth로 재설정하는 정책이 필요하다.

### 6. 용량 한계와 오버플로(Overflow) 처리

- **Hand 상한 10**: 손이 가득 찬 상태에서 카드를 더 더하려 하면 그 카드는 파괴된다(소위 overdraw/burn). 이렇게 파괴된 카드는 양 플레이어에게 공개된다 ([Hearthstone Hand](https://hearthstone.fandom.com/wiki/Hand), [Card draw](https://hearthstone.fandom.com/wiki/Card_draw)).
- **Field(board) 상한 7**: 친구 미니언 7기가 차면 추가 소환 불가. 미니언 카드/소환 효과가 플레이 불가가 되고, 7기를 채운 상태에서 발동하는 배틀크라이/데스래틀의 소환 효과는 낭비(무시)된다 ([Hearthstone Minion](https://hearthstone.fandom.com/wiki/Minion), [Board space](https://hearthstone.fandom.com/wiki/Board_space)).
- **Secret 상한 5**: 5개가 차면 추가 시크릿 효과는 무시된다 ([Hearthstone Battlefield](https://hearthstone.fandom.com/wiki/Battlefield)).
- **Deck 비었을 때 = Fatigue**: 빈 덱에서 드로우 시도가 실패할 때마다 누적 피해(1, 2, 3...)를 입는다. Fatigue는 드로우 실패 시에만 발생하며 영웅(hero)에 귀속된다. 덱을 다시 채우면 성공적 드로우 시 fatigue 피해가 없다 ([Hearthstone Fatigue](https://hearthstone.fandom.com/wiki/Fatigue), [PC Gamer - fatigue 유래](https://www.pcgamer.com/hearthstone-got-its-fatigue-mechanic-thanks-to-jeff-kaplan/)). 이는 프로토타입의 `fatigue` 필드와 정확히 대응한다.

오버플로 정책은 **"한계 도달 시 추가 진입을 거부하거나(field/secret) 즉시 파괴(hand)"** 두 패턴으로 요약된다.

### 7. 순차 해결 vs 스택 (본 게임의 명시적 단순화)

- MTG의 **stack은 LIFO(후입선출)** 존으로, 주문/능력을 쌓고 양 플레이어가 응답(우선권)할 수 있게 한 뒤 역순으로 해결한다 ([Draftsim - MTG Zones](https://draftsim.com/zones-mtg/), [tabletopmeta - The Stack](https://www.tabletopmeta.com/blog/mtg-stack-explained)).
- Hearthstone에는 진정한 상호작용 스택이 없고 대신 **Queue(대기열) 기반 순차 해결**을 쓴다: 동시 발생 이벤트(데미지/힐/죽음/손으로 복귀 등)는 플레이 순서로 큐잉되고, 각 이벤트에 대해 트리거가 다시 플레이 순서로 큐잉된다. **큐는 첫 항목 해결을 시작하는 순간 불변(immutable)이 되어 이후 새 항목을 추가할 수 없다** ([Deathrattle - 검색결과](https://hearthstone.fandom.com/wiki/Deathrattle), [Advanced rulebook 검색결과](https://hearthstone.fandom.com/wiki/Advanced_rulebook)).
- **본 프로토타입은 의도적으로 스택/체인을 배제**하므로, MTG 스택은 명백히 범위 외. 다만 Hearthstone식 "동시 죽음의 순차 처리"는 단순 버전이라도 고려할 가치가 있다(아래 함정 참고).

---

## 자료구조 · 코드 형태

### 1. 존을 `enum` + 리스트로 표현 (Forge의 실제 패턴)

오픈소스 MTG 엔진 **Forge**(Java)는 존 종류를 `ZoneType` enum으로 두고, 각 플레이어/게임이 존별 카드 컬렉션을 보유한다. 검색으로 확인된 실제 정의는 다음과 같다(`forge-game/.../zone/ZoneType.java`):

```
// "All" 질의 시 반환되는 정규 존 목록
List.of(Battlefield, Hand, Graveyard, Exile, Stack, Library, Command)
// 그 외 Ante, Sideboard, Flashback(가상 존) 등이 존재
```

(출처: [Card-Forge/forge code search](https://github.com/Card-Forge/forge) — `ZoneType.java`, `RestartGameEffect.java`, `CMatchUI.java`)

존 접근은 `player.getCardsIn(ZoneType.Hand)`, `player.getZoneSize(ZoneType.Graveyard)`, `game.getAction().moveTo(ZoneType.Hand, card, ...)` 형태로 이뤄진다(출처: 위 code search의 `Player.java`, `DeltaSyncManager.java`, `ExploreEffect.java`). 즉 **"존 enum → 카드 컬렉션 매핑"** 과 **"moveTo(존, 카드)" 단일 이동 API** 가 핵심 패턴이다.

본 프로토타입은 이미 `Zone(name){cards: MutableList<Card>}`를 가지므로, 다음과 같이 정리하는 것이 자연스럽다(Hearthstone 스타일 Kotlin 구현과 비교 가능):

```kotlin
class Zone(val name: String, val capacity: Int? = null) {
    val cards: MutableList<Card> = mutableListOf()
    val isFull get() = capacity != null && cards.size >= capacity
}

// 단일 이동 진입점: 모든 존 변경을 여기로 통과시켜 트리거 발동을 일원화
fun moveCard(card: Card, from: Zone, to: Zone): Boolean {
    if (to.isFull) return false      // field(7)/secret(5) 오버플로 거부
    from.cards.remove(card)
    onLeave(card, from)              // Deathrattle 등 이탈 트리거
    card.resetTemporaryState()       // 존 이동 시 임시 상태 리셋(객체 정체성)
    to.cards.add(card)               // hand면 add 전에 10장 검사 → 초과 시 burn
    onEnter(card, to)                // Battlecry 등 진입 트리거
    return true
}
```

### 2. 덱은 "리스트의 끝/앞 = top/bottom" 으로

- 일반적 권장: 존마다 `List`(예: ArrayList) 하나. 드로우/디스카드/peek top-N/shuffle을 리스트 연산으로 처리하는 것이 표준 패턴이다 ([GameDev.net - card game design](https://gamedev.net/forums/topic/634905-python-card-game-objects-methods-and-design/5004237/), [System Design - Deck of Cards](https://nerohoop.gitbooks.io/system-design/content/deck-of-cards.html)).
- 덱을 `Stack`(엄밀한 LIFO 자료구조)으로 굳이 만들 필요는 없다. **"top = list.last() 또는 list.first()" 규칙만 일관되게 정하면** `MutableList`로 충분하다. 단 라이브러리 위치 삽입(top/bottom/특정 index) 효과가 있으면 index 접근이 되는 `List`가 유리하다(Forge의 `LibraryPosition` 참조).
- 셔플은 `Collections.shuffle` / Kotlin `shuffle()` 한 줄.

### 3. 카드 인스턴스 소유(ownership)와 이동

- 각 카드는 **하나의 존에만** 존재해야 한다. 이동은 항상 "from에서 제거 → to에 추가"의 원자적 연산. 두 존에 동시에 참조가 남는 버그가 흔하다(아래 함정).
- 소유자(owner)와 컨트롤러(controller)는 MTG에선 분리되지만, 본 프로토타입(2인, 컨트롤 탈취 효과 없음)에서는 **owner = controller로 단순화**해도 무방.

---

## 실제 게임 비교

### Hearthstone (가장 가까운 비교 대상)
- 존: Deck / Hand / Play(field) / Graveyard, 내부 태그로 SECRET/SETASIDE/REMOVEDFROMGAME ([Battlefield](https://hearthstone.fandom.com/wiki/Battlefield)).
- 상한: hand 10(초과 시 burn), board 7, secret 5 ([Hand](https://hearthstone.fandom.com/wiki/Hand), [Minion](https://hearthstone.fandom.com/wiki/Minion), [Battlefield](https://hearthstone.fandom.com/wiki/Battlefield)).
- 빈 덱 = Fatigue(누적 피해) ([Fatigue](https://hearthstone.fandom.com/wiki/Fatigue)).
- **스택 없음**, 대신 큐 기반 순차 해결 ([Deathrattle](https://hearthstone.fandom.com/wiki/Deathrattle)).
- field 위치/인접성이 게임플레이에 영향 ([Positional effect](https://hearthstone.fandom.com/wiki/Positional_effect)).
- → 본 프로토타입의 설계 목표와 정확히 일치. **이 게임이 비교의 1차 기준이다.**

### Magic: The Gathering (이론적 기준, 규칙이 가장 정교)
- 7개 존, public/hidden·ordered/unordered·shared/per-player 분류가 명문화 ([cardboard zones.rst](https://github.com/Julian/cardboard/blob/master/docs/rules/zones.rst), [Draftsim](https://draftsim.com/zones-mtg/)).
- 존 이동 시 "새 객체" 규칙 → 상태 리셋의 근거 ([cardboard zones.rst](https://github.com/Julian/cardboard/blob/master/docs/rules/zones.rst)).
- stack(LIFO)·exile·command는 본 프로토타입 범위 외이나 개념 참고용 ([tabletopmeta - The Stack](https://www.tabletopmeta.com/blog/mtg-stack-explained)).

### Yu-Gi-Oh! (참고)
- Main Deck / Extra Deck(최대 15) / Hand / Graveyard(GY) / Banished / Field(고정 슬롯 존) ([2021 Rules Update](https://www.yugioh-card.com/en/play/2021_rules_update/), [Yugipedia - Zone](https://yugipedia.com/wiki/Zone)).
- 특징: **고정 좌표 슬롯**(5 Monster Zone 등)으로 존을 표현 — Hearthstone/본 프로토타입의 가변 리스트와 다른 모델. Banished는 "어느 존에도 안 놓이는" 별도 영역 ([Yugipedia - Banish](https://yugipedia.com/wiki/Banish)). → 슬롯 고정 모델은 본 프로토타입엔 불필요.

### 오픈소스 엔진
- **Forge**(Java, MTG): `ZoneType` enum + 존별 컬렉션 + `moveTo` 패턴, EnumSet/EnumMap으로 존 집합 관리 ([Card-Forge/forge](https://github.com/Card-Forge/forge)).
- **fireplace**(Python, Hearthstone): 모든 게임 객체가 `Entity` 서브클래스, 카드는 `tags` 딕셔너리(GameTag→값)로 상태 보유, Zone은 `PLAY/DECK/HAND/GRAVEYARD/REMOVEDFROMGAME/SETASIDE/SECRET`, `Player.play()/summon()/give()` 로 이동, `CardList`가 존 컬렉션 관리 ([fireplace wiki](https://github.com/jleclanche/fireplace/wiki)).
- → 두 엔진 모두 **"enum/태그로 존 종류 + 리스트 컬렉션 + 단일 이동 메서드"** 패턴으로 수렴. 본 프로토타입의 방향과 동일.

---

## 흔한 함정

1. **반복 중 컬렉션 수정 (ConcurrentModificationException).** 한 존의 카드를 순회하며 트리거가 같은 존을 add/remove하면 예외 발생. 해결: 순회 전 **리스트 사본**을 떠서 돌거나 인덱스 기반 for-loop 사용. Forge도 이 문제를 실제로 겪고 수정했다 ([theserverside - ConcurrentModificationException](https://www.theserverside.com/blog/Coffee-Talk-Java-News-Stories-and-Opinions/fix-ConcurrentModificationException-java-fail-safe-fast-solve), [CCG HQ forum](https://slightlymagic.net/forum/viewtopic.php?f=52&t=5035&start=210)). Kotlin에서는 `for (c in zone.cards.toList())` 한 줄로 방지.

2. **이중 소속(두 존에 동시 참조).** 이동을 "add 먼저, remove 깜빡"으로 처리하면 카드가 두 존에 남는다. **항상 from에서 remove → to에 add를 단일 함수로 강제**하고 직접 리스트 조작을 금지.

3. **존 이동 시 임시 상태 미리셋.** field→graveyard→(부활)→field 시 이전 데미지/버프/`readyToAttack`가 남으면 버그. MTG의 "새 객체" 규칙대로 이동 시 baseAttack/baseHealth로 리셋해야 한다 ([cardboard zones.rst](https://github.com/Julian/cardboard/blob/master/docs/rules/zones.rst)).

4. **오버플로 처리 누락.** hand 11장째를 그냥 add하거나(상한 무시), board 8기째 소환을 허용하면 규칙 위반. add 전에 용량 검사 필수. hand 초과는 **거부가 아니라 파괴(burn) + 공개**라는 점에 주의 ([Hand](https://hearthstone.fandom.com/wiki/Hand)).

5. **빈 덱 드로우 = 크래시 또는 잘못된 패배.** 빈 리스트에서 pop하면 예외/즉사 처리하기 쉬운데, Hearthstone식은 **fatigue 누적 피해**다. 드로우 실패 경로를 명시적으로 분기해야 한다 ([Fatigue](https://hearthstone.fandom.com/wiki/Fatigue)).

6. **동시 죽음의 순서.** 여러 미니언이 동시에 죽으면 Hearthstone은 **먼저 전부 보드에서 제거한 뒤, 플레이(등장) 순서대로** 데스래틀 등 death 효과를 해결한다. 데미지 교환 도중에 죽음을 체크하지 않는다 ([Deathrattle](https://hearthstone.fandom.com/wiki/Deathrattle)). 데스래틀 도중 새로 채워진 트리거를 같은 처리 안에 끼워 넣지 않도록 큐를 불변 취급해야 한다 ([Advanced rulebook](https://hearthstone.fandom.com/wiki/Advanced_rulebook)). 단순 프로토타입이라도 "죽은 카드들을 한 번에 모아 graveyard로 옮긴 뒤 트리거 일괄 처리" 순서를 정해두면 안전.

7. **가시성 혼동.** field는 공개지만 deck/hand는 숨김, secret은 컨트롤러만 본다는 점을 UI/직렬화 단계에서 섞지 말 것 ([Hearthstone Battlefield](https://hearthstone.fandom.com/wiki/Battlefield), [cardboard zones.rst](https://github.com/Julian/cardboard/blob/master/docs/rules/zones.rst)). Android 이식 시 상대 hand/deck 내용은 클라이언트로 보내지 않는 설계가 정석.

8. **덱 top/bottom 규칙 불일치.** 드로우는 list.last인지 first인지, 셔플 후/되돌림 효과의 삽입 위치는 어디인지 한곳에 명문화하지 않으면 디버깅 지옥. MTG ordered-zone 개념을 빌려 "덱은 순서 있는 리스트"로 못 박을 것 ([MTG Wiki - Zone](https://mtg.fandom.com/wiki/Zone)).

---

## 출처 URL 목록

- MTG Wiki — Zone: https://mtg.fandom.com/wiki/Zone
- MTG Wiki — Leaves the battlefield: https://mtg.fandom.com/wiki/Leaves_the_battlefield
- MTG Wiki — Battlefield: https://mtg.fandom.com/wiki/Battlefield
- MTG Wiki — Exile: https://mtg.fandom.com/wiki/Exile
- cardboard (Julian) — zones.rst: https://github.com/Julian/cardboard/blob/master/docs/rules/zones.rst
- cardboard — Read the Docs (Zones): https://cardboard.readthedocs.io/en/latest/rules/zones.html
- Draftsim — MTG Zones Explained: https://draftsim.com/zones-mtg/
- tabletopmeta — MTG The Stack Explained: https://www.tabletopmeta.com/blog/mtg-stack-explained
- Knockoutsf — ETB Triggers guide: https://knockoutsf.com/whenever-a-creature-enters-the-battlefield-the-complete-guide-to-etb-triggers-in-magic-the-gathering/
- Hearthstone Wiki (Fandom) — Battlefield: https://hearthstone.fandom.com/wiki/Battlefield
- Hearthstone Wiki — Advanced rulebook: https://hearthstone.fandom.com/wiki/Advanced_rulebook
- Hearthstone Wiki (wiki.gg) — Advanced rulebook (rewrite draft): https://hearthstone.wiki.gg/wiki/User:Xinhuan/Advanced_rulebook_(rewrite)
- Hearthstone Wiki — Hand: https://hearthstone.fandom.com/wiki/Hand
- Hearthstone Wiki — Card draw: https://hearthstone.fandom.com/wiki/Card_draw
- Hearthstone Wiki — Minion: https://hearthstone.fandom.com/wiki/Minion
- Hearthstone Wiki — Board space: https://hearthstone.fandom.com/wiki/Board_space
- Hearthstone Wiki — Fatigue: https://hearthstone.fandom.com/wiki/Fatigue
- Hearthstone Wiki — Deathrattle: https://hearthstone.fandom.com/wiki/Deathrattle
- Hearthstone Wiki — Positional effect: https://hearthstone.fandom.com/wiki/Positional_effect
- PC Gamer — Hearthstone fatigue 유래: https://www.pcgamer.com/hearthstone-got-its-fatigue-mechanic-thanks-to-jeff-kaplan/
- Yu-Gi-Oh! — 2021 Rules Update: https://www.yugioh-card.com/en/play/2021_rules_update/
- Yugipedia — Zone: https://yugipedia.com/wiki/Zone
- Yugipedia — Banish: https://yugipedia.com/wiki/Banish
- Card-Forge/forge (오픈소스 MTG 엔진, ZoneType.java 등): https://github.com/Card-Forge/forge
- jleclanche/fireplace (오픈소스 Hearthstone 시뮬레이터) wiki: https://github.com/jleclanche/fireplace/wiki
- TheCardGoat/tcg-engines (TypeScript TCG 엔진): https://github.com/TheCardGoat/tcg-engines
- GameDev.net — Python card game 설계 토론: https://gamedev.net/forums/topic/634905-python-card-game-objects-methods-and-design/5004237/
- System Design — Deck of Cards: https://nerohoop.gitbooks.io/system-design/content/deck-of-cards.html
- theserverside — ConcurrentModificationException 해결: https://www.theserverside.com/blog/Coffee-Talk-Java-News-Stories-and-Opinions/fix-ConcurrentModificationException-java-fail-safe-fast-solve
- Collectible Card Game HQ forum (Forge 개발 버그 사례): https://slightlymagic.net/forum/viewtopic.php?f=52&t=5035&start=210
