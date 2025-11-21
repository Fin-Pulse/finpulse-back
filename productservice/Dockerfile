FROM --platform=$TARGETPLATFORM eclipse-temurin:17-jdk-jammy

WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY ./productservice/target/productservice-0.0.1-SNAPSHOT.jar app.jar

RUN groupadd -r spring && useradd -r -g spring spring
USER spring

EXPOSE 8085

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8085/api/notifications/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]