# ---- Stage 1: Build ----
FROM node:20-alpine AS builder
WORKDIR /app

# Dependency layer — cached unless package files change
COPY package.json package-lock.json* ./
RUN npm ci --frozen-lockfile

# Source layer
COPY . .

# VITE_* env vars are baked at build time; override via build args if needed
ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

# Google Identity Services client ID (Profile page's link/unlink-Google flow,
# vite.config.ts's `define` reads this via loadEnv's empty-prefix merge of
# process.env). Must equal the backend's spring.security.oauth2.client.
# registration.google.client-id — GoogleTokenVerifier checks the ID token's
# aud claim against it.
ARG GOOGLE_CLIENT_ID=
ENV GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}

RUN npm run build

# ---- Stage 2: Runtime ----
FROM nginx:alpine AS runtime

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
