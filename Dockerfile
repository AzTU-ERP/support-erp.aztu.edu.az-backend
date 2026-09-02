# syntax=docker/dockerfile:1

# ---- Build stage: compile the Spring Boot jar with the Maven wrapper ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Copy the wrapper first so the Maven download layer caches independently of src.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/
# Cache the local Maven repo across builds (BuildKit). Tests need Postgres which
# isn't available during image build, so they're skipped here.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp clean package -DskipTests

# ---- Runtime stage: slim JRE image running the fat jar ----
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Run as a non-root user.
RUN groupadd -r app && useradd -r -g app app \
    && mkdir -p /app/support-storage \
    && chown -R app:app /app

COPY --from=build /workspace/target/*.jar app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
