# FinPay service image (ADR-0012 baseline: Java 21 LTS, Spring Boot 4.1.0, Gradle 9.7).
# Builds the bootJar inside the container so the image is self-contained.
# The `finpay-platform` composite-build submodule must be present at build time
# (checked out as a git submodule in CI); it is git-ignored in the repo.
FROM gradle:9.7.0-jdk21-ubi AS build
WORKDIR /home/gradle
COPY --chown=gradle:gradle . .
USER gradle
RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre-ubi9-minimal
WORKDIR /app
COPY --from=build /home/gradle/build/libs/*.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
HEALTHCHECK CMD wget -q -O- http://localhost:${SERVER_PORT:-8080}/actuator/health/liveness || exit 1
ENTRYPOINT exec java $JAVA_OPTS -jar app.jar
