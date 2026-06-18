package com.lsk.cardgame.config

import com.lsk.cardgame.BuildConfig

/**
 * 온라인 대전 설정값. gradle 프로퍼티(SERVER_URL, GOOGLE_WEB_CLIENT_ID)로 주입된 BuildConfig 값을 읽는다.
 * 둘 다 비어 있으면 온라인 기능은 비활성(안내 표시)되고 로컬 AI 대전은 그대로 동작한다.
 *
 * 설정 방법(택1): gradle.properties / local.properties / `-PSERVER_URL=...` / 환경변수 ORG_GRADLE_PROJECT_SERVER_URL
 */
object AppConfig {
    /** WebSocket 서버 베이스 주소. 예: `wss://your-host` (경로 `/play` 는 클라이언트가 덧붙임). */
    val serverUrl: String get() = BuildConfig.SERVER_URL

    /** 구글 OAuth "웹 애플리케이션" 클라이언트 ID — 서버가 검증하는 ID 토큰의 audience. */
    val googleWebClientId: String get() = BuildConfig.GOOGLE_WEB_CLIENT_ID

    /** 온라인 대전 사용 가능 여부(서버 주소 + 웹 클라이언트 ID 모두 설정되어야 함). */
    val isOnlineConfigured: Boolean get() = serverUrl.isNotBlank() && googleWebClientId.isNotBlank()
}
