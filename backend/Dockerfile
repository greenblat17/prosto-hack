FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
COPY gradlew ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/build/libs/*.jar app.jar
RUN chown -R app:app /app

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
