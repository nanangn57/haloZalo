# Build context is services/. Analytics is installed first because messaging records events with that jar.
#   docker compose up --build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY analytics analytics
COPY messaging messaging
RUN mvn -q -B -DskipTests -f analytics/pom.xml install && mvn -q -B -DskipTests -f messaging/pom.xml package

# Run stage: JRE only, non-root user.
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 messaging \
    && mkdir -p /var/lib/halozalo/media && chown messaging /var/lib/halozalo/media
WORKDIR /app
# Uploads. Mount a volume here, or files are lost with the container.
ENV MESSAGING_MEDIA_DIR=/var/lib/halozalo/media
COPY --from=build /build/messaging/target/messaging-0.1.0.jar app.jar
USER messaging
EXPOSE 8082
# Size the heap from the container memory limit, not the host.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# Exec form keeps Java as PID 1, so it gets SIGTERM and closes sockets with 1001 before stopping.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
