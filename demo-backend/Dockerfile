# Eclipse Temurin JRE 21 Alpine
FROM eclipse-temurin:21-jre-alpine

LABEL description="Demo Backend Upstream Service for Distributed Rate Limiter"

WORKDIR /app

COPY demo-backend/target/demo-backend.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
