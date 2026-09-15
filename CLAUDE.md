# OmniSuite — Agent Onboarding

Offline-first Android document suite (Compose + Hilt + Room). Single app, no flavors. Release via `bundleRelease` to Play internal track.

## Docs

- `docs/agents.md` — feature → file map, routes, quick-fix index
- `docs/architecture.md` — layers, MVVM data flow
- `docs/file-map.md` — full file catalog
- `docs/navigation.md` — `Screen` routes
- `docs/dependencies.md` — versions, R8 rules
- `docs/engines.md` — conversion/search/image engine APIs

## Release

`.github/workflows/deploy.yml` on push to `main`:
`versionCode = github.run_number + 2`, `versionName = 1.0.{code}` passed as `-PAPP_VERSION_CODE` / `-PAPP_VERSION_NAME`. Secrets required: `ANDROID_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `PLAY_STORE_JSON_KEY`. Never commit `*.jks`, `keystore.properties`, `play-store-key.json`.

## Guidelines

1. State assumptions. Ask when unclear.
2. Minimal diffs. No speculative features.
3. Match existing style. Touch only what the task needs.
4. Define verify step per change (compile, unit test, or workflow run).
