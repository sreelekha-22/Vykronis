# Build stage: compile the demo-service module (repo root is the build context).
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY contracts ./contracts
COPY libs ./libs
COPY platform ./platform
RUN mvn -q -pl platform/demo-service -am package -DskipTests

# Runtime stage.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/platform/demo-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
