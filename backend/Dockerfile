FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
COPY gradlew ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd -r app && useradd -r -g app app
RUN mkdir -p /data && chown -R app:app /data
COPY --from=build /app/build/libs/*.jar app.jar
RUN chown -R app:app /app

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
