# Lecturo

A personal document reader for **PDF and EPUB** built around a single idea:
while reading, you select a passage and **ask AI** about it. Asking makes a
**draft** — an unsent conversation that carries the passage as its frozen
context — and when the first reply lands, the draft becomes a **conversation**
about that passage, not the whole book.

One product, four codebases, one shared vocabulary (see
[`PRD.md`](PRD.md), the unified product requirements document).

## Stacks

| Directory | What it is | Stack |
|---|---|---|
| [`lecturo-android/`](lecturo-android/) | The reference client — a local, single-user Android app | Kotlin · Jetpack Compose · Room · `androidx.pdf` + Readium |
| [`lecturo-web/`](lecturo-web/) | Responsive browser client with a server-side library | Angular (Material 3) · Go · Postgres · S3-compatible blob store |
| [`lecturo-desktop/`](lecturo-desktop/) | Native desktop app, Linux-first | .NET 10 · Avalonia 12 · `CommunityToolkit.Mvvm` |
| [`lecturo-relay/`](lecturo-relay/) | The keyless **Lecturo Cloud** backend for testers | Kotlin/JVM · JDK `HttpServer` · kotlinx.serialization |

## The loop

1. **Read** a document, with the last position restored.
2. **Select** a passage (or crop a screenshot) and choose **Ask AI**.
3. **Draft** — the excerpt becomes frozen context on an unsent conversation.
4. **Send** — the first reply sets the conversation in motion.
5. **Converse** — messages are answered against the frozen excerpt, compacting
   older turns when the model's context budget would be exceeded.
6. **Keep** — conversations persist, resumable, renameable, deletable.

## Run

```sh
# Android (needs the Android SDK)
cd lecturo-android && ./gradlew :app:assembleDebug

# Web (Postgres + SeaweedFS via Docker)
cd lecturo-web && docker compose up -d
cd web && pnpm install && pnpm start
cd server && go run .

# Desktop
cd lecturo-desktop && dotnet run

# Relay (Lecturo Cloud)
UPSTREAM_BASE_URL=https://api.example.com/v1 \
UPSTREAM_API_KEY=sk-... \
UPSTREAM_MODEL=some-vision-model \
./gradlew -p lecturo-relay run
```

Each stack has its own README with build, test and environment details.

## Documentation

- [`PRD.md`](PRD.md) — the unified product requirements document: goals, domain
  vocabulary, functional requirements and per-platform divergences.
- Per-client `CONTEXT.md` and `docs/` — architecture, ADRs, interactions and
  design references.

## License

MIT.
