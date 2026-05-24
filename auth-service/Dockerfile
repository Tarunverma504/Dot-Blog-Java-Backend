# syntax=docker/dockerfile:1.7
# Build context must be the dot-blog-backend/ root (compose handles this).
# Why: auth-service depends on the `shared/api-contract` module and the
# parent pom.xml, all of which live above the service folder.

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Maven wrapper + parent POM first for better layer caching.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# The parent pom.xml lists every sibling module, and Maven validates that
# each one exists on disk before applying -pl filters. So we copy all
# sibling folders; -pl + -am still ensures only auth-service and the
# modules it depends on are actually compiled.
COPY shared shared
COPY gateway gateway
COPY auth-service auth-service
COPY user-service user-service
COPY blog-service blog-service
COPY engagement-service engagement-service
COPY media-service media-service

# Build only auth-service and the modules it depends on. Tests are skipped
# inside the image (run them in CI on the host instead).
RUN chmod +x mvnw \
 && ./mvnw -B -pl auth-service -am clean package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for the running JVM process.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /workspace/auth-service/target/auth-service-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
