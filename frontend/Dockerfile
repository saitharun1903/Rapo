# syntax=docker/dockerfile:1

# ---- Dependencies: reused until package-lock.json changes ------------------------------------------------------
FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

# ---- Build: standalone output (a minimal server.js and only the traced node_modules) ---------------------------
FROM node:24-alpine AS build
WORKDIR /app
# next.config and NEXT_PUBLIC_ variables are fixed at build time, so the image is built for where it runs:
# BACKEND_URL is where the server forwards /api (the backend service in compose); NEXT_PUBLIC_WS_URL is where
# the browser opens the WebSocket (the backend's published port).
ARG BACKEND_URL=http://backend:8080
ARG NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
ARG NEXT_PUBLIC_MAP_STYLE_URL
ARG NEXT_PUBLIC_MAP_CENTER_LAT
ARG NEXT_PUBLIC_MAP_CENTER_LNG
ENV BACKEND_URL=$BACKEND_URL \
    NEXT_PUBLIC_WS_URL=$NEXT_PUBLIC_WS_URL \
    NEXT_PUBLIC_MAP_STYLE_URL=$NEXT_PUBLIC_MAP_STYLE_URL \
    NEXT_PUBLIC_MAP_CENTER_LAT=$NEXT_PUBLIC_MAP_CENTER_LAT \
    NEXT_PUBLIC_MAP_CENTER_LNG=$NEXT_PUBLIC_MAP_CENTER_LNG \
    NEXT_TELEMETRY_DISABLED=1 \
    NEXT_OUTPUT=standalone
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# public/ holds no files today (the icon lives in src/app), and git keeps no empty directories; create it so
# the runtime stage can copy it either way.
RUN mkdir -p public && npm run build

# ---- Runtime: Node only, non-root ------------------------------------------------------------------------------
FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# server.js serves public/ and .next/static/ when they sit next to it.
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
COPY --from=build --chown=node:node /app/public ./public

USER node
EXPOSE 3000
# 127.0.0.1, not localhost: in this image localhost resolves to ::1 first, and the server listens on IPv4.
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=6 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/" || exit 1

CMD ["node", "server.js"]
