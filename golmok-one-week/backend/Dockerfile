FROM gradle:8.14.3-jdk21 AS build
WORKDIR /app

COPY gradle gradle
COPY gradlew gradlew.bat build.gradle settings.gradle ./
COPY src src

RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --create-home --uid 10001 golmok
COPY --from=build /app/build/libs/*.jar app.jar
USER golmok

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
