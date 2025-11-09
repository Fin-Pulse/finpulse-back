#FROM eclipse-temurin:17-jre-alpine
#
#WORKDIR /app
#
#COPY target/notificationservice-0.0.1-SNAPSHOT.jar app.jar
#
#EXPOSE 8084
#
#ENTRYPOINT ["java", "-jar", "app.jar"]
#FROM eclipse-temurin:17-jdk-jammy
#
#WORKDIR /app
#
## Install curl for health check
#RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
#
## Copy the JAR file from module target directory
#COPY ./notificationservice/target/notificationservice-0.0.1-SNAPSHOT.jar app.jar
#
## Create non-root user
#RUN groupadd -r spring && useradd -r -g spring spring
#USER spring
#
#EXPOSE 8084
#
#HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
#    CMD curl -f http://localhost:8084/api/notifications/health || exit 1
#
#ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM --platform=$TARGETPLATFORM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Install curl for health check
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy the JAR file from module target directory
COPY ./notificationservice/target/notificationservice-0.0.1-SNAPSHOT.jar app.jar

# Create non-root user
RUN groupadd -r spring && useradd -r -g spring spring
USER spring

EXPOSE 8084

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8084/api/notifications/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]