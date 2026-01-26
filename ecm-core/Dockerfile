# syntax=docker/dockerfile:1.6
# Multi-stage build for ECM Core
FROM maven:3.9-eclipse-temurin-17 AS build

# Set working directory
WORKDIR /app

# Copy pom.xml and lombok.config first
COPY pom.xml .
COPY lombok.config .

# Download dependencies first (separate layer for caching)
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the project (with clean to ensure fresh compilation)
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jdk

# Optional: skip LibreOffice to speed up local builds
ARG SKIP_LIBREOFFICE=false

# Install runtime tools (curl is required for healthcheck)
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    fontconfig \
    fonts-liberation \
    fonts-dejavu-core \
    && if [ "$SKIP_LIBREOFFICE" != "true" ]; then \
         apt-get install -y libreoffice; \
       else \
         echo "Skipping LibreOffice install"; \
       fi \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Create app user
RUN groupadd -r app && useradd -r -g app app

# Set working directory
WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Create directories for content storage
RUN mkdir -p /var/ecm/content /var/ecm/temp /app/logs \
    && chown -R app:app /var/ecm /app

# Copy entrypoint (handles volume permission fixes)
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Start application
ENTRYPOINT ["/entrypoint.sh"]
