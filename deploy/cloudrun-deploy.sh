#!/usr/bin/env bash
# Cloud Run deploy script — cph (core API, context path /cph)
#
# Phase 23-07 (INFRA-04): deploy-ready artifact only. This script is NOT run
# by any automated pipeline (D-08 — GitHub Actions auto-deploy is documented,
# not enabled). The user runs this manually with `gcloud` authenticated
# against the target project. See ../../DEPLOYMENT.md for the full plan,
# cost table, and composability notes.
#
# Prerequisites (see DEPLOYMENT.md + 23-04-SUMMARY.md "User Setup Required"):
#   - gcloud CLI authenticated (`gcloud auth login`) with deploy permission
#   - Artifact Registry Docker repo already created and populated by the
#     dual-push CI (23-04) — this script does not build or push the image
#   - GCP Secret Manager secrets created with the SAME values as the VM's
#     existing env (see the list under --set-secrets below)
#   - An Upstash serverless Redis database provisioned (D-14) — TLS endpoint
#     host/port recorded, password stored in Secret Manager as redis-password
#
set -euo pipefail

# ─── Required overrides (fail loudly if unset — never silently deploy with
#     a guessed value for anything environment-specific) ──────────────────────
PROJECT_ID="${GCP_PROJECT_ID:?Set GCP_PROJECT_ID (the GCP project the dual-push CI in 23-04 targets)}"
AIVEN_DB_HOST="${AIVEN_DB_HOST:?Set AIVEN_DB_HOST to the Aiven pranic_doc Postgres host (Aiven console -> connection info)}"
UPSTASH_REDIS_HOST="${UPSTASH_REDIS_HOST:?Set UPSTASH_REDIS_HOST — Upstash console -> database -> connection details (TLS)}"

# ─── Optional overrides (sane defaults; adjust per Claude's Discretion /
#     actual cost-vs-latency tradeoff at deploy time) ─────────────────────────
REGION="${GCP_REGION:-us-central1}"
ARTIFACT_REPO="${GCP_ARTIFACT_REPO:?Set GCP_ARTIFACT_REPO (the Artifact Registry Docker repo created per 23-04 User Setup Required)}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
AIVEN_DB_PORT="${AIVEN_DB_PORT:-5432}"
UPSTASH_REDIS_PORT="${UPSTASH_REDIS_PORT:-6379}"
MIN_INSTANCES="${MIN_INSTANCES:-0}"
MAX_INSTANCES="${MAX_INSTANCES:-3}"
CONCURRENCY="${CONCURRENCY:-80}"
CPU="${CPU:-1}"
MEMORY="${MEMORY:-1Gi}"

IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${ARTIFACT_REPO}/cph:${IMAGE_TAG}"

# ─── D-01 composability: inter-service URLs are env-configured, never
#     hardcoded Docker DNS names (e.g. "http://creditservice:9112" only works
#     inside the docker-compose network). Fill these in with the actual
#     Cloud Run service URLs once creditService/UI are deployed. ────────────
CREDIT_SERVICE_URL="${CREDIT_SERVICE_URL:?Set CREDIT_SERVICE_URL to the creditService Cloud Run URL, e.g. https://creditservice-xxxx-uc.a.run.app/credits, once deployed}"
APP_BASE_URL="${APP_BASE_URL:?Set APP_BASE_URL to the UI Cloud Run URL or custom domain post-cutover, D-06}"
APP_FEEDBACK_URL="${APP_FEEDBACK_URL:-$APP_BASE_URL}"
APP_INVITE_URL="${APP_INVITE_URL:-$APP_BASE_URL}"

# ─── D-03 amended: Aiven-managed Postgres, sslmode=require, no pg_hba edits.
#     Overriding spring.datasource.url/username directly (rather than the
#     DB_HOST/DB_PORT placeholders application.yaml's local/VM profile uses)
#     is the only way to inject sslmode=require without editing app source —
#     Spring Boot's environment-variable PropertySource outranks
#     application-prod.yml, so SPRING_DATASOURCE_URL below fully replaces the
#     yaml-templated value; DB_HOST/DB_PORT are simply never consulted. ─────
SPRING_DATASOURCE_URL="jdbc:postgresql://${AIVEN_DB_HOST}:${AIVEN_DB_PORT}/pranic_doc?sslmode=require"

gcloud run deploy cph \
  --project="${PROJECT_ID}" \
  --image="${IMAGE}" \
  --region="${REGION}" \
  --platform=managed \
  --port=9918 \
  --cpu="${CPU}" \
  --memory="${MEMORY}" \
  --min-instances="${MIN_INSTANCES}" \
  --max-instances="${MAX_INSTANCES}" \
  --concurrency="${CONCURRENCY}" \
  --set-env-vars="SPRING_PROFILES_ACTIVE=prod,\
JAVA_TOOL_OPTIONS=-XX:+UseContainerSupport -XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Dfile.encoding=UTF-8,\
SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL},\
SPRING_DATASOURCE_USERNAME=${DB_USERNAME:?Set DB_USERNAME (the Aiven app DB user)},\
GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID:?Set GOOGLE_CLIENT_ID},\
R2_ENDPOINT=${R2_ENDPOINT:?Set R2_ENDPOINT},\
APP_BASE_URL=${APP_BASE_URL},\
APP_FEEDBACK_URL=${APP_FEEDBACK_URL},\
APP_INVITE_URL=${APP_INVITE_URL},\
MAIL_HOST=${MAIL_HOST:?Set MAIL_HOST},\
MAIL_PORT=${MAIL_PORT:-587},\
MAIL_USERNAME=${MAIL_USERNAME:?Set MAIL_USERNAME},\
CREDIT_SERVICE_URL=${CREDIT_SERVICE_URL},\
WHATSAPP_PHONE_NUMBER_ID=${WHATSAPP_PHONE_NUMBER_ID:-},\
MANAGEMENT_HEALTH_REDIS_ENABLED=true,\
SPRING_DATA_REDIS_HOST=${UPSTASH_REDIS_HOST},\
SPRING_DATA_REDIS_PORT=${UPSTASH_REDIS_PORT},\
SPRING_DATA_REDIS_SSL_ENABLED=true" \
  --set-secrets="SPRING_DATASOURCE_PASSWORD=db-password:latest,\
JWT_SECRET=jwt-secret:latest,\
ENCRYPTION_KEY=encryption-key:latest,\
GOOGLE_CLIENT_SECRET=google-client-secret:latest,\
R2_ACCESS_KEY=r2-access-key:latest,\
R2_SECRET_KEY=r2-secret-key:latest,\
CREDIT_API_KEY=credit-api-key:latest,\
MAIL_PASSWORD=mail-password:latest,\
SPRING_DATA_REDIS_PASSWORD=redis-password:latest"

# ─── Notes ──────────────────────────────────────────────────────────────────
# - WHATSAPP_ACCESS_TOKEN / WHATSAPP_APP_SECRET / WHATSAPP_WEBHOOK_VERIFY_TOKEN
#   are deliberately NOT wired above: the plan's own Secret Manager
#   provisioning list (jwt-secret, encryption-key, db-password,
#   google-client-secret, r2-access-key, r2-secret-key, credit-api-key,
#   mail-password, redis-password) does not include WhatsApp secrets. If/when
#   FEED-01/FEED-02 need Cloud Run deployment, add three more Secret Manager
#   secrets and a --set-secrets entry each, following this same pattern —
#   do not pass them as plain --set-env-vars in a real deploy.
# - Kafka is not wired here: cph currently runs with
#   KafkaAutoConfiguration excluded (see application.yaml), consistent with
#   "Kafka producer disabled until Kafka is deployed" — nothing to configure
#   for Cloud Run yet.
# - Redis (D-14/D-17, closes 23-02 T-23-05): SPRING_DATA_REDIS_HOST/PORT
#   point at the Upstash TLS endpoint, never the local docker-compose `redis`
#   container hostname. SPRING_DATA_REDIS_SSL_ENABLED=true selects the
#   rediss:// scheme; the password is wired via --set-secrets, never inline.
# - Least-privilege IAM (T-23-18): grant this service's runtime service
#   account roles/secretmanager.secretAccessor scoped to exactly the 9
#   secrets above — not project-wide. See DEPLOYMENT.md.
