# Build context is services/. Analytics is installed first because identity depends on that jar.
#   docker compose up --build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY analytics analytics
COPY identity identity
RUN mvn -q -B -DskipTests -f analytics/pom.xml install && mvn -q -B -DskipTests -f identity/pom.xml package

# Run stage: JRE only, non-root user.
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 identity
WORKDIR /app
COPY --from=build /build/identity/target/identity-0.1.0.jar app.jar
USER identity
EXPOSE 8080
# Size the heap from the container memory limit, not the host.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
