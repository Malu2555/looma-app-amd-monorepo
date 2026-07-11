# ============================================================================
#  Multi-stage Dockerfile — Looma Assistant Frontend
#
#  Stage 1 : Build  — compile the Vite + Vue SPA
#  Stage 2 : Serve  — serve static assets via nginx (tiny, fast)
# ============================================================================

# ----------------------------------------------------------------------------
#  Stage 1 — Build
# ----------------------------------------------------------------------------
FROM node:22-alpine AS builder

WORKDIR /app

# Copy dependency manifests and install (cached separately from source)
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts --no-audit --no-fund

# Copy the rest of the source and build
COPY . .
RUN npm run build

# ----------------------------------------------------------------------------
#  Stage 2 — Serve with nginx
# ----------------------------------------------------------------------------
FROM nginx:1.27-alpine

# Remove default nginx config
RUN rm -f /etc/nginx/conf.d/default.conf

# Copy custom nginx config (listens on port 3000)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy built static assets from the builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Health check — nginx ships with curl
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:3000/health || exit 1

# Expose the port nginx listens on (matches nginx.conf)
EXPOSE 3000

# Run nginx in the foreground (default CMD from base image is fine)
CMD ["nginx", "-g", "daemon off;"]