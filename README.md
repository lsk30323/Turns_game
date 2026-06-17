# Turns

A turn-based card battler for one player versus an AI opponent. Every turn your mana grows,
letting you summon stronger minions, cast spells, and trigger battlecries, deathrattles, and
board-wide buffs. Trade creatures, manage your hand, and drop your rival's hero to zero to win.

콘솔 Kotlin 카드게임(`PlayableCardGame.kt`)을 **Jetpack Compose 안드로이드 앱**으로 포팅한 결과물.

## 아키텍처 (3계층, 의존성 방향 UI → ViewModel → domain)

```
app/src/main/java/com/lsk/cardgame/
├─ domain/         순수 Kotlin. android.* import 0. 규칙 엔진(카드·효과·존·이벤트·엔진).
├─ presentation/   GameViewModel(StateFlow<GameUiState>) + 불변 UI 스냅샷 모델
│  └─ ui/          GameScreen + 컴포넌트 + 테마
└─ MainActivity.kt
```

- **domain**은 안드로이드에 의존하지 않으며 단위 테스트로 한 판 시뮬레이션이 가능하다.
- 콘솔 → 앱 변환 3원칙 적용: `println` → 로그 콜백, `readLine` → `GameAction`(명령 패턴),
  가변 상태 → 불변 UI 스냅샷(`StateFlow`).
- 하스스톤식 **순차 해결** 모델(상호작용 스택/체인 없음). 규칙(턴/마나 +1·최대10/소환 멀미/반격/탈진/승패) 보존.

## 멀티 에이전트 연구·검수 산출물
- `research/` — 5개 시스템(존/카드데이터/효과/타이밍/상태직렬화) 리서치 보고서.
- `review/playablecardgame-review.md` — 기준 구현 대비 갭 분석(✅유지/⚠️반영/🟦스코프밖/📌권고).
- `review/backlog.md` — "나중" 권고.

## 빌드 / 실행
```bash
./gradlew :app:assembleDebug      # APK 빌드
./gradlew :app:installDebug       # 기기/에뮬레이터 설치
./gradlew :app:testDebugUnitTest  # 도메인 단위 테스트
```

### 적용 버전 (작업 시점 2026-06 안정 버전, `gradle/libs.versions.toml`)
| 항목 | 버전 |
|---|---|
| AGP | 8.7.3 |
| Gradle | 8.9 (wrapper) |
| Kotlin | 2.0.21 (Compose 컴파일러 플러그인 동버전) |
| Compose BOM | 2024.12.01 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| Coroutines / Lifecycle / Activity-Compose | 1.9.0 / 2.8.7 / 1.9.3 |

> **참고**: 이 저장소가 만들어진 CI 환경은 Google Maven(`maven.google.com`)이 차단되어
> AGP/AndroidX/Compose 의존성을 받을 수 없어 **전체 안드로이드 빌드는 로컬/CI에서 수행**해야 한다.
> 대신 순수 Kotlin `domain/` 은 Maven Central 만으로 컴파일·테스트가 가능하며,
> `.verify/` 하위에 그 검증용 임시 JVM 빌드를 두었다 (`cd .verify && gradle test`, 11개 테스트 통과 확인).
