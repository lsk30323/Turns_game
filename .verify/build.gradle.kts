// 로컬 검증 전용(앱과 무관). Google Maven 이 막힌 환경에서 Maven Central 만으로
// (1) 순수 코틀린 domain, (2) GameViewModel(androidx.lifecycle 스텁 주입)을
// 컴파일/테스트하기 위한 임시 JVM 빌드. Compose ui/ 와 MainActivity 는 제외.
plugins {
    kotlin("jvm") version "2.0.21"
}
repositories { mavenCentral() }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
sourceSets {
    main {
        kotlin.srcDir("stubs")                       // androidx.lifecycle.ViewModel 스텁
        kotlin.srcDir("../app/src/main/java")        // domain + presentation(model, GameViewModel)
        kotlin.exclude("**/presentation/ui/**")      // Compose UI 제외(Google Maven 필요)
        kotlin.exclude("**/MainActivity.kt")         // Activity 제외
    }
    test {
        kotlin.srcDir("../app/src/test/java")        // 도메인 단위 테스트
        kotlin.srcDir("vmtest")                      // ViewModel 검증 테스트
    }
}
tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(21) }
