# Build stage: compile the api-gateway module (repo root is the build context).
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY contracts ./contracts
COPY libs ./libs
COPY platform ./platform
RUN mvn -q -pl platform/api-gateway -am package -DskipTests

# Runtime stage: shared slim jlink JRE (infra/docker/runtime.Dockerfile ->
# vykronis/runtime:local; must be built once before this image).
FROM vykronis/runtime:local
WORKDIR /app
COPY --from=build /build/platform/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
