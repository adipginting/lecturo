# Lecturo relay

The small server behind the app's **Lecturo Cloud** provider: testers point the
app at a relay instead of bringing their own API key, and the relay holds the
one upstream key and meters what each install may spend.

It exists to be boring. One process, one external dependency (the upstream
API), and one piece of state: a counter per device per day. It does not log
messages, store conversations, or see anything it does not have to pass
through — your testers' excerpts transit it, nothing more.

## What it does

- `POST /v1/chat/completions` — OpenAI-shaped pass-through. The caller
  identifies itself with `Authorization: Bearer <device id>`; the app sends its
  anonymous install id there. The relay checks the day's quota, replaces the
  `model` field with its own, forwards the request upstream, returns the
  response verbatim, and counts the tokens the upstream reported.
- `GET /v1/models` — reports the one model, so the app's model picker works.
- `GET /health` — `{"status":"ok"}`.

Quota denials are `429` with an OpenAI-shaped error body, so the app shows the
message as-is. The only plan today is `free`, decided server-side: the app
contains no business logic about limits.

## Run it

```sh
# from the repository root
UPSTREAM_BASE_URL=https://api.example.com/v1 \
UPSTREAM_API_KEY=sk-... \
UPSTREAM_MODEL=some-vision-model \
./gradlew -p lecturo-relay run
```

Required: `UPSTREAM_BASE_URL`, `UPSTREAM_API_KEY`, `UPSTREAM_MODEL`.
Optional: `RELAY_PORT` (8787), `FREE_DAILY_REQUESTS` (50),
`FREE_DAILY_TOKENS` (100000), `QUOTA_FILE` (relay-quota.json).

The upstream must speak the OpenAI chat-completions API; pick one whose model
reads images, because the app's screenshot feature is on for this provider.

For an emulator, the app reaches the host as `10.0.2.2`: paste
`http://10.0.2.2:8787` into Settings → Lecturo Cloud → Relay address (the app
appends `/chat/completions` and `/models` to whatever base it is given; the
relay answers both the bare and the `/v1`-prefixed spellings). Leave the API
key field blank — the app generates a per-install device id and sends it
instead.

## Deploy notes

- Run it behind TLS (any reverse proxy) before handing testers an address; the
  device id in the `Authorization` header is a bearer token and deserves the
  same treatment as one.
- One process is enough for tens of testers. `QUOTA_FILE` is the only state to
  back up, and losing it merely resets today's counters.
- A paid plan is a new `Plan` on the server plus, later, a billing hook —
  nothing in the app changes shape.
