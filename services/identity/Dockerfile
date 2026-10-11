# Build stage: dependencies are downloaded in their own layer, so code changes do not download them again.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
COPY resources resources
RUN mvn -q -B -DskipTests package

# Run stage: JRE only, non-root user.
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 identity
WORKDIR /app
COPY --from=build /build/target/identity-0.1.0.jar app.jar
USER identity
EXPOSE 8080
# Size the heap from the container memory limit, not the host.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
