# Lecturo Web — build and run.
#
# `make` / `make all` builds the single deployable: the Angular app is compiled
# and embedded into the Go binary. `make run` starts it against local services.

WEB_DIR := web
EMBED_DIR := server/internal/infrastructure/webui/dist

.PHONY: all web server run test lint check clean

all: web server

## Build the Angular app and copy it into the embed directory.
web:
	cd $(WEB_DIR) && pnpm install && pnpm build
	rm -rf $(EMBED_DIR)
	mkdir -p $(EMBED_DIR)
	cp -r $(WEB_DIR)/dist/lecturo-web/browser/. $(EMBED_DIR)/

## Build the Go binary (requires `make web` first for a real UI).
server:
	cd server && go build ./...

## Run the API + embedded UI. Needs Postgres + an S3-compatible blob store:
##   docker compose up -d
run:
	cd server && go run .

## All tests, including the integration ones when the services are up.
test:
	cd server && go test ./...

lint:
	cd server && gofmt -l . && go vet ./...
	cd $(WEB_DIR) && pnpm exec prettier --check "src/**/*.{ts,html,scss}"

clean:
	rm -rf $(WEB_DIR)/dist $(EMBED_DIR)
