# ---- Build Stage ----
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./

# Download dependencies (cached layer)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source
COPY src src

# Build
RUN ./mvnw clean package -DskipTests -B

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="OpenPHC <info@openphc.org>"
LABEL service="cce-compliance-service"

# Create non-root user
RUN addgroup -S cce && adduser -S cce -G cce

WORKDIR /app

# Copy the built artifact
COPY --from=builder /app/target/compliance-service-*.jar app.jar

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

# Expose application port
EXPOSE 8080

# Switch to non-root user
USER cce

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
