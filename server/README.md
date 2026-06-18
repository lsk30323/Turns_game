# Turns 온라인 PvP 서버

Ktor + WebSocket **권위 서버**. 도메인 규칙 엔진(`:domain`)을 그대로 재사용해 서버가 모든 판정을 수행하고,
각 플레이어에겐 자기 관점의 스냅샷만 보낸다(상대 손패는 수량만 노출 → 치팅 방지).

- 엔드포인트: `GET /health`(헬스체크), `WS /play`(게임)
- 프로토콜: `:domain` 의 `com.lsk.cardgame.domain.net`(`ClientMessage`/`ServerMessage`/`GameView`, JSON)
- 매칭: 빠른 대전 큐 + 방 코드(친구 초대)

## 로컬 실행
```bash
SERVER_ONLY=true ./gradlew :server:run        # 기본 8080 포트
SERVER_ONLY=true ./gradlew :domain:test :server:test   # 테스트
```
> `SERVER_ONLY=true` 면 `:app`(Android) 을 빼고 구성 → Android SDK/Google Maven 없이 빌드된다.

## 배포 (무료 호스팅 예)
빌드 산출물은 어디든 배포 가능한 컨테이너다. `server/Dockerfile` 사용.

**Fly.io**
```bash
fly launch --dockerfile server/Dockerfile --no-deploy
fly deploy
```
**Render** : New → Web Service → 저장소 연결 → Runtime = Docker, Dockerfile path = `server/Dockerfile`.
**Railway** : New → Deploy from repo → Dockerfile 자동 감지.

호스트가 `$PORT` 를 주입하면 서버가 그 포트로 listen 한다. 배포 후 받은 도메인을
안드로이드 앱의 서버 주소(`wss://<your-host>/play`)로 설정하면 온라인 대전이 동작한다.

> ⚠️ 무료 티어는 유휴 시 잠자기(cold start) 가 있어 첫 연결이 느릴 수 있다.

## 정식 계정 (구글 로그인 + 전적·랭킹)
온라인 참가 시 클라이언트가 **구글 ID 토큰**을 보내고, 서버가 검증해 계정을 식별한다.
게임 종료 시 승/패·레이팅(±25)을 기록하고, `GET /leaderboard` 로 랭킹(JSON)을 제공한다.

**서버 환경변수**
| 변수 | 설명 |
|---|---|
| `GOOGLE_OAUTH_CLIENT_ID` | 앱이 받은 ID 토큰의 audience(= **웹** OAuth 클라이언트 ID). 미설정이면 모든 로그인 실패. |
| `DATABASE_URL` | Postgres 연결 문자열(`postgres://user:pass@host:port/db`). 미설정이면 인메모리(재시작 시 휘발). |

**본인이 해야 할 Google Cloud 설정 (내가 대신 못 함)**
1. [Google Cloud Console](https://console.cloud.google.com) → 프로젝트 생성 → OAuth 동의 화면 구성.
2. 사용자 인증 정보 → OAuth 클라이언트 ID **2개** 생성:
   - **Android** 용(앱 패키지명 `com.lsk.cardgame` + 서명 키 **SHA-1**). → 앱에서 로그인에 사용.
   - **웹 애플리케이션** 용. → 이 client ID 를 서버 `GOOGLE_OAUTH_CLIENT_ID` 와 앱의 `serverClientId` 에 넣는다(ID 토큰 audience).
3. 영속 전적이 필요하면 무료 Postgres(Neon/Supabase/Render) 생성 후 `DATABASE_URL` 설정.

> 엔드포인트: `WS /play`(게임·로그인), `GET /leaderboard`(랭킹), `GET /health`.
