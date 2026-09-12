# Build stage: compile the agent-orchestrator module (repo root is the build context).
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY contracts ./contracts
COPY libs ./libs
COPY platform ./platform
RUN mvn -q -pl platform/agent-orchestrator -am package -DskipTests

# Runtime stage: shared slim jlink JRE (infra/docker/runtime.Dockerfile ->
# vykronis/runtime:local; must be built once before this image).
FROM vykronis/runtime:local
WORKDIR /app
COPY --from=build /build/platform/agent-orchestrator/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Train stage: derive a per-service AppCDS archive in the same runtime image
# (guarantees JVM compatibility) from a bounded boot that brings the full
# application class set up, then dump the shared archive from the class list.
FROM vykronis/runtime:local AS train
WORKDIR /app
COPY --from=build /build/platform/agent-orchestrator/target/*.jar app.jar
RUN timeout 90 java -XX:DumpLoadedClassList=classlist.txt \
        -jar app.jar --server.port=0 --management.server.port=0 \
        > /dev/null 2>&1 || true \
    && java -Xshare:dump \
        -XX:SharedArchiveFile=/app/app.jsa \
        -XX:SharedClassListFile=classlist.txt \
        -jar app.jar

# Final runtime stage with the trained AppCDS archive.
FROM vykronis/runtime:local
WORKDIR /app
COPY --from=build /build/platform/agent-orchestrator/target/*.jar app.jar
COPY --from=train /app/app.jsa /app/app.jsa
EXPOSE 8085
ENTRYPOINT ["java", "-XX:SharedArchiveFile=/app/app.jsa", "-jar", "/app/app.jar"]
