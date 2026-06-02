# syntax=docker/dockerfile:1.6

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Cache deps in their own layer
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn -B -q -DskipTests dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests package && \
    cp target/*.jar /build/app.jar

FROM eclipse-temurin:21-jre-alpine

# Non-root user
RUN addgroup -S gateway && adduser -S gateway -G gateway
USER gateway

WORKDIR /app
COPY --from=builder /build/app.jar app.jar

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -q -O - http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
