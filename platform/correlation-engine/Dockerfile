# Build stage: compile the correlation-engine module (repo root is the build context).
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY contracts ./contracts
COPY libs ./libs
COPY platform/correlation-engine ./platform/correlation-engine
RUN mvn -q -pl platform/correlation-engine -am package -DskipTests

# Runtime stage.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/platform/correlation-engine/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
