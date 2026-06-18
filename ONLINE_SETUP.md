# 온라인 대전 설정 가이드 (2단계)

오프라인 AI 대전은 설정 없이 바로 동작한다. **온라인 대전(정식 계정·전적·랭킹)** 을 실제로 켜려면
아래 4가지를 본인이 준비해야 한다(제가 대신 못 하는 외부 작업).

## 1. 권위 서버 배포
`:server` 모듈을 호스팅한다(Fly.io / Render / Railway 등). 빌드 산출물:
```bash
./gradlew :server:installDist
# 실행: server/build/install/server/bin/server  (PORT 환경변수 사용)
```
배포 후 받은 도메인이 `wss://<your-host>` 다(경로 `/play` 는 앱이 자동으로 붙임).

## 2. 데이터베이스 (전적·랭킹 영속화)
서버 환경변수 `DATABASE_URL` 에 무료 Postgres 연결 문자열(Neon/Supabase/Render)을 넣는다.
미설정 시 인메모리로 동작하지만 **서버 재시작 시 전적이 사라진다**.
```
DATABASE_URL=postgres://user:pass@host:5432/dbname
```

## 3. Google OAuth 클라이언트 ID 2개
[Google Cloud Console](https://console.cloud.google.com) → OAuth 동의 화면 구성 후 사용자 인증 정보에서:
1. **Android** 클라이언트 — 패키지명 `com.lsk.cardgame` + 서명 키 **SHA-1** 등록.
   - 디버그 SHA-1: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
2. **웹 애플리케이션** 클라이언트 — 이 ID 가 ID 토큰의 audience 다.
   - 서버 환경변수 `GOOGLE_OAUTH_CLIENT_ID` 와
   - 앱 빌드 프로퍼티 `GOOGLE_WEB_CLIENT_ID` 에 **동일하게** 넣는다.

## 4. 앱 빌드에 서버 주소·웹 클라이언트 ID 주입
코드를 수정할 필요 없이 gradle 프로퍼티로 주입한다(택1):

`gradle.properties` (또는 `~/.gradle/gradle.properties`):
```properties
SERVER_URL=wss://your-host
GOOGLE_WEB_CLIENT_ID=xxxxxxxx.apps.googleusercontent.com
```
또는 빌드 시 옵션으로:
```bash
./gradlew :app:assembleRelease -PSERVER_URL=wss://your-host -PGOOGLE_WEB_CLIENT_ID=xxxx.apps.googleusercontent.com
```
둘 다 비어 있으면 앱은 "온라인 설정 필요" 안내를 표시하고, **AI 대전은 정상 동작**한다.

---

## 동작 흐름 요약
1. 메뉴에서 **온라인 대전** → **구글로 로그인**(Credential Manager).
2. 앱이 ID 토큰을 서버로 전송 → 서버가 검증(`tokeninfo`, aud/iss/exp) → 계정 조회/생성 → 프로필 응답.
3. **빠른 대전**(랜덤 매칭) 또는 **방 코드**(친구와 같은 코드)로 매칭.
4. 권위 서버가 도메인 엔진으로 규칙을 처리하고, 각 플레이어 관점의 `GameView` 스냅샷을 내려준다.
5. 게임 종료 시 승/패·레이팅(±25) 기록 → 갱신된 프로필 표시. **🏆 랭킹 보기**로 상위 랭킹 확인.

## 엔드포인트
- `WS /play` — 로그인·매칭·게임
- `GET /leaderboard` — 랭킹(JSON)
- `GET /health` — 헬스체크
