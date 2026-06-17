// 로컬 도메인 검증 전용(앱과 무관). Google Maven 이 막힌 환경에서
// 순수 코틀린 domain 패키지만 컴파일/테스트하기 위한 임시 JVM 빌드.
plugins {
    kotlin("jvm") version "2.0.21"
}
repositories { mavenCentral() }
dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
sourceSets {
    main { kotlin.srcDir("../app/src/main/java/com/lsk/cardgame/domain") }
    test { kotlin.srcDir("../app/src/test/java/com/lsk/cardgame/domain") }
}
tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(21) }
