#!/usr/bin/env bash
# Cloud Run deploy script — cph-ui (React SPA, nginx-static container)
#
# Phase 23-07 (INFRA-04): deploy-ready artifact only. Not run by any
# automated pipeline (D-08). Cloudflare Pages/cloudflared/nginx keep serving
# prod traffic unchanged until DNS cutover (D-10, D-06) — see
# ../../../DEPLOYMENT.md for the retirement plan.
#
set -euo pipefail

# ─── Required overrides ────────────────────────────────────────────────────
PROJECT_ID="${GCP_PROJECT_ID:?Set GCP_PROJECT_ID (the GCP project the dual-push CI in 23-04 targets)}"

# ─── Optional overrides ─────────────────────────────────────────────────────
REGION="${GCP_REGION:-us-central1}"
ARTIFACT_REPO="${GCP_ARTIFACT_REPO:?Set GCP_ARTIFACT_REPO (Artifact Registry Docker repo created per 23-04 User Setup Required)}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
MIN_INSTANCES="${MIN_INSTANCES:-0}"
MAX_INSTANCES="${MAX_INSTANCES:-2}"
CONCURRENCY="${CONCURRENCY:-200}"
CPU="${CPU:-1}"
MEMORY="${MEMORY:-256Mi}"

IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${ARTIFACT_REPO}/cph-ui:${IMAGE_TAG}"

# No --set-secrets here: this is a static nginx container serving a
# build-time-baked SPA (VITE_API_BASE_URL is compiled into the bundle at
# image-build time via a Docker ARG, not read at container runtime) — there
# is no runtime env/secret surface to configure. --port must match
# cph/ui/nginx.conf's `listen 8080;` (23-07's PORT-contract change) and
# cph/ui/Dockerfile's EXPOSE 8080.
gcloud run deploy cph-ui \
  --project="${PROJECT_ID}" \
  --image="${IMAGE}" \
  --region="${REGION}" \
  --platform=managed \
  --port=8080 \
  --cpu="${CPU}" \
  --memory="${MEMORY}" \
  --min-instances="${MIN_INSTANCES}" \
  --max-instances="${MAX_INSTANCES}" \
  --concurrency="${CONCURRENCY}" \
  --allow-unauthenticated

# ─── Notes ──────────────────────────────────────────────────────────────────
# - --allow-unauthenticated is required here: this is a public-facing SPA
#   (equivalent to today's Cloudflare Pages/nginx-fronted access), not an
#   internal service. cph/creditService/pdfWorker's scripts deliberately omit
#   this flag (Cloud Run's default is auth-required for those, matching
#   their current nginx-fronted-only reachability in docker-compose) — see
#   DEPLOYMENT.md's CORS/cookie section for what changes when this becomes
#   the real prod ingress on the custom domain (D-06).
# - If VITE_API_BASE_URL needs to change for a given deploy, rebuild the
#   image with a different --build-arg VITE_API_BASE_URL=... (dual-push CI,
#   23-04) — this script only deploys an already-built image, it cannot
#   change baked-in build-time values.
