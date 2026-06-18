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
