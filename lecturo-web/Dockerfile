# syntax=docker/dockerfile:1

# --- Angular SPA -------------------------------------------------------------
FROM node:22-slim AS web
RUN corepack enable
WORKDIR /src/web
COPY web/package.json web/pnpm-lock.yaml web/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY web/ ./
RUN pnpm build

# --- Go API + embedded SPA ---------------------------------------------------
FROM golang:1.26 AS server
WORKDIR /src/server
COPY server/go.mod server/go.sum ./
RUN go mod download
COPY server/ ./
# `go:embed all:dist` bakes the SPA into the binary; the directory must exist.
RUN mkdir -p internal/infrastructure/webui/dist
COPY --from=web /src/web/dist/lecturo-web/browser/ ./internal/infrastructure/webui/dist/
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/lecturo-web .

# --- Runtime -----------------------------------------------------------------
FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=server /out/lecturo-web /lecturo-web
ENV LECTURO_ADDR=:8787
EXPOSE 8787
USER nonroot:nonroot
ENTRYPOINT ["/lecturo-web"]
