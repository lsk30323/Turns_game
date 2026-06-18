// 공용 도메인 모듈 — 순수 Kotlin. 안드로이드 :app 과 JVM :server 가 모두 의존한다.
// android.* import 0. Java 11 바이트코드로 컴파일해 Android(min26)와 서버(JVM17) 모두 호환.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // 네트워크 프로토콜 직렬화에 사용(클라이언트/서버 공용 메시지·뷰 DTO).
    api(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
