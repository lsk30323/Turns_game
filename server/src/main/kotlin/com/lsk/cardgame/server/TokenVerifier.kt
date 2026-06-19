package com.lsk.cardgame.server

import com.lsk.cardgame.domain.net.ProtocolJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/** 구글 ID 토큰 검증 결과(안정 식별자 sub + 표시 이름 + 이메일). */
data class VerifiedUser(val sub: String, val name: String, val email: String?)

/** 구글 ID 토큰을 검증해 신원을 돌려준다. 테스트는 가짜 구현으로 대체. */
fun interface TokenVerifier {
    suspend fun verify(idToken: String): VerifiedUser?
}

@Serializable
private data class GoogleTokenInfo(
    val aud: String? = null,
    val sub: String? = null,
    val name: String? = null,
    val email: String? = null,
    val exp: String? = null,
    val iss: String? = null,
)

/**
 * 구글 tokeninfo 엔드포인트로 ID 토큰을 검증한다(추가 의존성 없이 JDK HttpClient 사용).
 * aud(우리 OAuth 클라이언트 ID) · iss · exp 를 확인한다.
 *
 * @param expectedAudience 안드로이드 앱이 받은 ID 토큰의 audience(= 웹 OAuth 클라이언트 ID).
 *                         비어 있으면(미설정) 항상 실패 → 온라인 로그인 비활성과 동일.
 */
class GoogleTokenVerifier(
    private val expectedAudience: String,
    private val http: HttpClient = HttpClient.newHttpClient(),
) : TokenVerifier {
    override suspend fun verify(idToken: String): VerifiedUser? {
        if (expectedAudience.isBlank() || idToken.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(idToken, StandardCharsets.UTF_8)
                val request = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=$encoded"))
                    .GET().build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() != 200) return@runCatching null
                val info = ProtocolJson.decodeFromString<GoogleTokenInfo>(response.body())
                val sub = info.sub ?: return@runCatching null
                if (info.aud != expectedAudience) return@runCatching null
                if (info.iss != "accounts.google.com" && info.iss != "https://accounts.google.com") return@runCatching null
                val expSeconds = info.exp?.toLongOrNull() ?: return@runCatching null
                if (expSeconds < System.currentTimeMillis() / 1000) return@runCatching null
                VerifiedUser(sub = sub, name = info.name ?: info.email ?: "Player", email = info.email)
            }.getOrNull()
        }
    }
}
