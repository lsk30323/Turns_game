plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 온라인 대전 설정값(서버 주소 + 구글 웹 클라이언트 ID)은 gradle 프로퍼티로 주입한다.
// gradle.properties / local.properties / -P 옵션 / 환경변수(ORG_GRADLE_PROJECT_*) 중 하나로 설정.
// 미설정 시 빈 문자열 → 앱은 "온라인 미설정" 안내를 표시하고 AI 대전은 정상 동작.
val serverUrl: String = (project.findProperty("SERVER_URL") as String?)?.trim().orEmpty()
val googleWebClientId: String = (project.findProperty("GOOGLE_WEB_CLIENT_ID") as String?)?.trim().orEmpty()

android {
    namespace = "com.lsk.cardgame"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lsk.cardgame"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.android)

    // 온라인 대전: 권위 서버와 WebSocket 통신(공용 :domain 프로토콜 직렬화)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)

    // 정식 계정: 구글 로그인(Credential Manager + Sign in with Google)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    debugImplementation(libs.androidx.ui.tooling)

    // 도메인 단위 테스트 (android.* 의존성 0)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
