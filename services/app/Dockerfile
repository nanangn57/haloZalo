# Build context is services/.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY analytics analytics
COPY identity identity
COPY messaging messaging
COPY app app
RUN mvn -q -B -DskipTests -f analytics/pom.xml install \
 && mvn -q -B -DskipTests -f identity/pom.xml install \
 && mvn -q -B -DskipTests -f messaging/pom.xml install \
 && mvn -q -B -DskipTests -f app/pom.xml package

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 appuser \
 && mkdir -p /var/halozalo/media \
 && chown -R appuser:appuser /var/halozalo
WORKDIR /app
COPY --from=build /build/app/target/app-0.1.0.jar app.jar
USER appuser
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
