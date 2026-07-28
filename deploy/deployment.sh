#!/usr/bin/env bash
# One-command local deploy wrapper — cph (core API)
#
# Runs the manual parts of DEPLOYMENT.md's Deploy Runbook (section 11,
# steps 3 + 6 + 7 + 8) that aren't already covered by cloudrun-deploy.sh:
#   1. Ensures the 9 Secret Manager secrets exist (create-if-missing, never
#      overwrites an existing secret's value).
#   2. Grants each secret's accessor role to the Cloud Run runtime service
#      account, scoped per-secret (T-23-18 — never project-wide).
#   3. Calls cloudrun-deploy.sh to actually deploy.
#   4. Smoke-tests the deployed service's health endpoint.
#
# This script is NOT wired into CI (D-08 — see ../.github/workflows/
# deploy-cloudrun.yml, which is deliberately left commented out until cph
# has proven itself via this manual path first). Run it yourself with
# gcloud authenticated against the target project.
#
# Secret values are read from environment variables and piped to `gcloud
# secrets create` via stdin — never passed as a CLI argument (which would
# leak into shell history / `ps`) and never written to disk.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PROJECT_ID="${GCP_PROJECT_ID:?Set GCP_PROJECT_ID (same project cloudrun-deploy.sh targets)}"

echo "==> Verifying gcloud auth..."
if ! gcloud auth list --filter=status:ACTIVE --format='value(account)' --project="${PROJECT_ID}" | grep -q .; then
  echo "ERROR: no active gcloud account. Run 'gcloud auth login' first." >&2
  exit 1
fi

# ─── Secret name : env var to read the value from : human description ─────
SECRETS=(
  "db-password:DB_PASSWORD:Aiven pranic_doc Postgres app-user password"
  "jwt-secret:JWT_SECRET:JWT signing secret (shared cph/pdfWorker)"
  "encryption-key:ENCRYPTION_KEY:App-level field encryption key"
  "google-client-secret:GOOGLE_CLIENT_SECRET:Google OAuth client secret"
  "r2-access-key:R2_ACCESS_KEY:Cloudflare R2 access key"
  "r2-secret-key:R2_SECRET_KEY:Cloudflare R2 secret key"
  "credit-api-key:CREDIT_API_KEY:Shared cph<->creditService API key"
  "mail-password:MAIL_PASSWORD:SMTP mail account password"
  "redis-password:REDIS_PASSWORD:Upstash Redis database password"
)

echo "==> Ensuring Secret Manager secrets exist in project ${PROJECT_ID}..."
for entry in "${SECRETS[@]}"; do
  IFS=':' read -r secret_name env_var description <<<"$entry"

  if gcloud secrets describe "${secret_name}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
    echo "    [skip]   ${secret_name} already exists"
    continue
  fi

  value="${!env_var:-}"
  if [[ -z "${value}" ]]; then
    echo "ERROR: secret '${secret_name}' does not exist yet and \$${env_var} is unset." >&2
    echo "       Set ${env_var} to: ${description}" >&2
    exit 1
  fi

  echo "    [create] ${secret_name}  (${description})"
  printf '%s' "${value}" | gcloud secrets create "${secret_name}" \
    --project="${PROJECT_ID}" \
    --replication-policy=automatic \
    --data-file=-

  # Grant per-secret access to the Cloud Run runtime SA, scoped to this one
  # secret only (T-23-18 — never a project-wide grant).
  if [[ -n "${CLOUD_RUN_SERVICE_ACCOUNT:-}" ]]; then
    gcloud secrets add-iam-policy-binding "${secret_name}" \
      --project="${PROJECT_ID}" \
      --member="serviceAccount:${CLOUD_RUN_SERVICE_ACCOUNT}" \
      --role="roles/secretmanager.secretAccessor" \
      >/dev/null
    echo "             granted secretAccessor to ${CLOUD_RUN_SERVICE_ACCOUNT}"
  fi
done

if [[ -z "${CLOUD_RUN_SERVICE_ACCOUNT:-}" ]]; then
  echo "    NOTE: CLOUD_RUN_SERVICE_ACCOUNT not set — skipped IAM grants."
  echo "          If cph's runtime SA can't read these secrets yet, set"
  echo "          CLOUD_RUN_SERVICE_ACCOUNT and re-run (grants are idempotent)."
fi

echo "==> Deploying cph via cloudrun-deploy.sh..."
"${SCRIPT_DIR}/cloudrun-deploy.sh"

echo "==> Smoke-testing deployed service..."
REGION="${GCP_REGION:-us-central1}"
SERVICE_URL="$(gcloud run services describe cph \
  --project="${PROJECT_ID}" \
  --region="${REGION}" \
  --format='value(status.url)')"

if [[ -z "${SERVICE_URL}" ]]; then
  echo "WARNING: could not resolve cph's Cloud Run URL — skipping smoke test." >&2
else
  HEALTH_URL="${SERVICE_URL}/cph/actuator/health"
  echo "    curling ${HEALTH_URL} (requires an identity token — cph is not --allow-unauthenticated)"
  STATUS=$(curl -s -o /tmp/cph-health-response.json -w '%{http_code}' \
    -H "Authorization: Bearer $(gcloud auth print-identity-token --audiences="${SERVICE_URL}")" \
    "${HEALTH_URL}" || true)
  if [[ "${STATUS}" == "200" ]]; then
    echo "    OK (200): $(cat /tmp/cph-health-response.json)"
  else
    echo "WARNING: health check returned HTTP ${STATUS} — inspect manually:" >&2
    echo "         ${HEALTH_URL}" >&2
    cat /tmp/cph-health-response.json >&2 2>/dev/null || true
  fi
  rm -f /tmp/cph-health-response.json
fi

echo "==> Done. Service URL: ${SERVICE_URL}"
