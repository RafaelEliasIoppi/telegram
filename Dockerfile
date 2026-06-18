# syntax=docker/dockerfile:1

# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom first to leverage Docker layer cache for dependencies
COPY teste/pom.xml ./pom.xml
RUN mvn -B -q dependency:go-offline

# Copy sources and build
COPY teste/src ./src
RUN mvn -B -q package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the fat jar produced by Spring Boot (exclude the *.original artifact)
COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 3000
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
