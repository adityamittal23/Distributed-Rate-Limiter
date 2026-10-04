# Eclipse Temurin JRE 21 Alpine - Lightweight & High Performance
FROM eclipse-temurin:21-jre-alpine

LABEL description="Distributed Rate Limiter Gateway & Real-Time Visualization Dashboard"

WORKDIR /app

# Copy the packaged Spring Boot artifact containing:
# 1. Distributed Rate Limiter Gateway (Redis Lua Engine, Sliding Window, Token Bucket)
# 2. Built-in Real-Time Frontend Dashboard (static/index.html)
COPY gateway/target/gateway.jar app.jar

# Expose Gateway & Frontend Dashboard HTTP Port
EXPOSE 8080

# Container-friendly JVM memory flags
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
