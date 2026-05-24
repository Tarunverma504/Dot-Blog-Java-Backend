# syntax=docker/dockerfile:1.7
# Build context must be the dot-blog-backend/ root.

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# The parent pom.xml lists every sibling module and Maven validates that
# each one exists on disk before applying -pl filters, so we copy all
# sibling folders. -pl + -am ensures only gateway and its dependencies
# are actually compiled.
COPY shared shared
COPY gateway gateway
COPY auth-service auth-service
COPY user-service user-service
COPY blog-service blog-service
COPY engagement-service engagement-service
COPY media-service media-service

RUN chmod +x mvnw \
 && ./mvnw -B -pl gateway -am clean package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /workspace/gateway/target/gateway-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
