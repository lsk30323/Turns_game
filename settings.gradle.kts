pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TurnsGame"

// 공용 도메인 + 온라인 PvP 서버는 안드로이드 SDK/Google Maven 없이 빌드 가능.
// SERVER_ONLY=true 이면 :app(Android) 을 빼고 :domain/:server 만 구성한다.
// → 서버 Docker 빌드와, Google Maven 차단 환경에서의 도메인/서버 로컬 검증에 사용.
include(":domain")
include(":server")
if (System.getenv("SERVER_ONLY") != "true") {
    include(":app")
}
