# Vykronis shared slim runtime — a jlink-customized JRE with a baked AppCDS
# archive, used as the runtime stage of every platform service image.
#
# Build once, from the repo root:
#   docker build -f infra/docker/runtime.Dockerfile -t vykronis/runtime:local .

FROM eclipse-temurin:25-jdk AS jdk

# Curated module set for the whole platform (Spring Boot 4, Kafka + Kafka
# Streams, JPA/Flyway, Spring AI RestClient, actuator, JFR streaming). Wider
# than a single app needs so one runtime serves all eight services, yet far
# smaller than the full JRE. --generate-cds-archive bakes the default AppCDS
# archive into the runtime, which is auto-loaded via -Xshare:auto.
RUN jlink \
    --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.naming,java.net.http,java.prefs,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,jdk.charsets,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.httpserver,jdk.jfr,jdk.localedata,jdk.naming.dns,jdk.naming.rmi,jdk.security.auth,jdk.security.jgss,jdk.unsupported,jdk.xml.dom,jdk.zipfs \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=2 \
    --generate-cds-archive \
    --output /opt/vykronis-jre

# Runtime stage: keep the glibc / certs / tz skeleton of the base image but
# drop the bundled full JRE, leaving only the slim jlink runtime.
FROM eclipse-temurin:25-jre
COPY --from=jdk /opt/vykronis-jre /opt/vykronis-jre
RUN rm -rf /opt/java/openjdk /usr/lib/jvm \
    && find /usr/bin -maxdepth 1 -type l -exec sh -c 'for f; do [ "$(readlink "$f")" = "/opt/java/openjdk/bin/$(basename "$f")" ] && rm "$f"; done' sh {} +
ENV PATH=/opt/vykronis-jre/bin:$PATH