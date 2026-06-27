# ---- Stage 1: Build ----
FROM gradle:8.10-jdk17-alpine AS builder
WORKDIR /app

ARG GPR_USER
ARG GPR_KEY

# Dependency layer — cached unless build files change
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon \
      -Pgpr.user=${GPR_USER} -Pgpr.key=${GPR_KEY} \
      --quiet 2>/dev/null || true

# Source layer
COPY src/ src/
RUN gradle bootJar -x test --no-daemon --quiet \
      -Pgpr.user=${GPR_USER} -Pgpr.key=${GPR_KEY} && \
    find build/libs/ -name "*.jar" ! -name "*-plain.jar" \
      -exec mv {} build/libs/app.jar \;

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

RUN apk add --no-cache curl

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /app/build/libs/app.jar ./app.jar

EXPOSE 9918

# JVM tuning is injected at runtime via JAVA_TOOL_OPTIONS in docker-compose / K8s deployment
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
