# syntax=docker/dockerfile:1

# Demo only. Node 24 runs the TypeScript sources directly (type stripping), so there is no build step and
# only the runtime dependency is installed.
FROM node:25-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --omit=dev --no-audit --no-fund
COPY --chown=node:node src ./src
USER node
CMD ["node", "src/index.ts"]
