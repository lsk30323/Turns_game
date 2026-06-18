# 온라인 PvP 서버 이미지 (멀티스테이지). SERVER_ONLY=true 로 :app(Android) 없이 빌드 →
# Android SDK 불필요, Maven Central 만 사용. 어느 컨테이너 호스트(Fly.io/Render/Railway 등)에도 배포 가능.
# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
ENV SERVER_ONLY=true
RUN chmod +x gradlew && ./gradlew --no-daemon :server:installDist

FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
COPY --from=build /src/server/build/install/server /app
# 호스트가 주입하는 $PORT 를 main() 이 읽는다(기본 8080).
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]
