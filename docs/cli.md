# dcc-cli

`dcc-cli` is the command-line execution client for DCC. It is an **HTTP client** over
the `dcc-application` `/cli/v1/*` API — it holds no business logic; the single
implementation lives in `dcc-core` (reached through `dcc-application`).

## Configure

| Setting | Env var | Default |
| --- | --- | --- |
| Backend base URL | `DCC_SERVER_URL` | `http://localhost:8080` |
| Bearer token | `DCC_TOKEN` | (none) |
| API path version | `DCC_API_VERSION` | `v1` |
| Connect timeout (s) | `DCC_CONNECT_TIMEOUT_SECONDS` | `10` |
| Request timeout (s) | `DCC_REQUEST_TIMEOUT_SECONDS` | `60` |

`--server <url>` overrides the URL for one invocation. The CLI sends
`Authorization: Bearer <token>` when `DCC_TOKEN` is set. `DCC_API_VERSION` must match
the server's `dcc.api.version`.

## Commands

```
dcc hash     --type T --model-id N --version V
dcc diff     --type T --model-id N --from V1 --to V2
dcc bump     --type T --model-id N --from V1 --to V2
dcc validate --type T --model-id N --version V
dcc export   --type T --model-id N --version V --format java|sql
dcc ci       --env-id E --app-id A
```

`T` is one of `DATA_STANDARD`, `TABLE_STRUCTURE`, `INTERFACE`, `INTERFACE_TEMPLATE`.
`--output json` prints the raw JSON (stable for scripting/MCP wrapping).

Exit codes: `0` success, `1` failure (invalid model, drift detected, transport error),
`2` usage error.

`dcc ci` runs a drift check (`validate` across the environment/application scope) and
exits non-zero when any stored snapshot no longer matches its recorded hash — suitable
for a PR/pipeline gate.

## Build and run

```bash
./mvnw -pl dcc-cli -am package
java -jar dcc-cli/target/dcc-cli-0.0.1-SNAPSHOT.jar hash --type DATA_STANDARD --model-id 1 --version 1.0.0
```

## The `/cli/<version>/*` API

Command-shaped, POST + JSON. The version segment comes from the server's
`dcc.api.version` (default `v1`).

| Endpoint | Body | Response |
| --- | --- | --- |
| `POST /cli/v1/hash` | `{type, modelId, version}` | `{hash}` |
| `POST /cli/v1/diff` | `{type, modelId, fromVersion, toVersion}` | `{changes[]}` |
| `POST /cli/v1/bump` | `{type, modelId, fromVersion, toVersion}` | `{level, reasons[], changes[]}` |
| `POST /cli/v1/validate` | `{type, modelId, version}` | `{valid, errors[]}` |
| `POST /cli/v1/export/java` | `{type, modelId, version}` | `{files[{path, content}]}` |
| `POST /cli/v1/export/sql` | `{type, modelId, version}` | `{files[{path, content}]}` |
| `POST /cli/v1/drift` | `{environmentId, applicationId}` | `{drifted[]}` |

This group (`/cli/v1`) is separate from the richer web group (`/api/*`); both share the
same `dcc-application` / `dcc-core`. The edge gateway routes both `/api` and `/cli` to
`dcc-web` (see `docker-compose.yml`).

## Relation to MCP

`dcc-mcp` is an integration adapter (protocol translator) — it maps MCP calls onto the
same query capabilities. MCP stays read-mostly and exposes few tools to keep the tool
schema small. Neither MCP nor the CLI re-implements logic.
