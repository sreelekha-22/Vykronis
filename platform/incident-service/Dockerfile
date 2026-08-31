# Build stage: compile the incident-service module (repo root is the build context).
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY contracts ./contracts
COPY libs ./libs
COPY platform/incident-service ./platform/incident-service
RUN mvn -q -pl platform/incident-service -am package -DskipTests

# Runtime stage.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/platform/incident-service/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
