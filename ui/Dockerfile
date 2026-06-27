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

RUN npm run build

# ---- Stage 2: Runtime ----
FROM nginx:alpine AS runtime

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
