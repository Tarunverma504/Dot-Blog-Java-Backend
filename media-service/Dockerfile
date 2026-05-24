# syntax=docker/dockerfile:1.7
# Build context must be the dot-blog-backend/ root.

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Parent pom.xml validates that every declared sibling module exists, so
# copy them all. -pl + -am ensures only media-service + its dependencies
# are actually compiled.
COPY shared shared
COPY gateway gateway
COPY auth-service auth-service
COPY user-service user-service
COPY blog-service blog-service
COPY engagement-service engagement-service
COPY media-service media-service

RUN chmod +x mvnw \
 && ./mvnw -B -pl media-service -am clean package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /workspace/media-service/target/media-service-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
