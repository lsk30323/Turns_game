package com.lsk.cardgame.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * 구글 로그인(Credential Manager + Sign in with Google). 성공 시 서버에 보낼 ID 토큰을 반환한다.
 * [context] 는 자격 증명 UI 를 띄울 Activity 컨텍스트여야 한다.
 */
class GoogleAuthClient(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)

    /** @param serverClientId 구글 OAuth "웹" 클라이언트 ID (= 서버가 검증할 토큰 audience). */
    suspend fun signIn(serverClientId: String): Result<String> = runCatching {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false) // 가입 계정 없이도 모든 구글 계정으로 로그인 허용
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = credentialManager.getCredential(context, request)
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            error("지원하지 않는 자격 증명 유형입니다")
        }
    }
}
