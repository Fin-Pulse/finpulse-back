FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Install curl for health check
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy the JAR file from module target directory
COPY ./aggregationservice/target/aggregationservice-0.0.1-SNAPSHOT.jar app.jar

# Create non-root user
RUN groupadd -r spring && useradd -r -g spring spring
USER spring

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8082/api/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]