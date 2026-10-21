# dcc-cli

`dcc-cli` is the command-line client for DCC. It is a standalone **Go module** under
`dcc-cli/` and a thin HTTP client over `dcc-web`: resource maintenance uses `/api/*`,
command verbs use `/cli/<version>/*`. It holds no business logic — the single
implementation remains in `dcc-core`, reached through `dcc-application`.

The former Java `dcc-cli` module no longer exists and is not part of the Maven reactor.
The CLI follows the structure and conventions of GitHub's `gh`:

```
dcc-cli/
├── cmd/dcc/main.go            process entry
├── api/                       HTTP transport and wire types
├── internal/                  config, change notes, jq/template, rendering
└── pkg/cmd/<noun>/<verb>      command tree (Cobra)
```

## Install

Requirements: Go 1.24+.

```bash
# From the repository root
make -C dcc-cli build            # ./dcc-cli/bin/dcc
make -C dcc-cli install          # $GOBIN/dcc

# Or directly
cd dcc-cli && go install ./cmd/dcc
```

Release binaries are produced by GoReleaser for darwin/linux/windows on amd64/arm64
(see `.goreleaser.yaml`).

## Quick start

```bash
dcc auth login --hostname dcc.example.com --token-stdin < token.txt
dcc auth status
dcc standard list --environment-id 1 --application-id 2
dcc standard view 7
dcc standard history 7
dcc standard diff 7 --from 1.0.0 --to 1.1.0
```

For a local backend:

```bash
dcc auth login --server http://localhost:8080 --token "$DCC_TOKEN"
```

## Configuration

Hosts are stored in `hosts.yml` with mode `0600`:

- `$DCC_CONFIG_DIR/hosts.yml`
- else `$XDG_CONFIG_HOME/dcc/hosts.yml`
- else `~/.config/dcc/hosts.yml`

```yaml
current: dcc.example.com
hosts:
  dcc.example.com:
    server_url: https://dcc.example.com
    token: eyJ...
    api_version: v1
    connect_timeout_seconds: 10
    request_timeout_seconds: 60
    insecure_skip_tls_verify: false
```

Resolution precedence:

1. flags: `--server`, `--hostname`, `--api-version`
2. environment: `DCC_SERVER_URL`, `DCC_TOKEN`, `DCC_API_VERSION`,
   `DCC_CONNECT_TIMEOUT_SECONDS`, `DCC_REQUEST_TIMEOUT_SECONDS`, `DCC_HOSTNAME`
3. `hosts.yml`
4. defaults: `http://localhost:8080`, `v1`, 10s connect, 60s request

`DCC_TOKEN` takes precedence over the stored token, so CI can run without writing a
hosts file. Tokens are stored in plain text in `hosts.yml` for now; use `DCC_TOKEN` or
`--token-stdin` where that matters.

## Commands

Global flags:

```
--hostname HOST        configured host (default: current)
--server URL           one-shot base URL override
--api-version V        /cli path version (default: v1)
--json[=FIELDS]        JSON output, optionally a field list
--jq EXPR              filter JSON output
--template TEMPLATE    render JSON output with a Go template
--no-color             disable colour (NO_COLOR is also honoured)
```

`--json` alone prints the complete payload. Field selection requires `=`, for example
`--json=id,code,currentVersion` (pflag's optional-value syntax). Page responses keep
their envelope and project `content[]` element-wise.

### Authentication

```
dcc auth login  [--hostname H] [--token T | --token-stdin] [--insecure-skip-tls-verify]
dcc auth status [--hostname H]
dcc auth logout [--hostname H] [--all]
```

`login` validates the token against `GET /api/me` before saving it. `status` reports the
effective host, token source and `/api/me` result, and exits 1 when not authenticated.

### Artifact nouns

`standard`, `table`, `interface` and `template` share the same verb set:

```
dcc <noun> list [--environment-id E] [--application-id A] [--status S]
dcc <noun> view <id>
dcc <noun> history <id>
dcc <noun> diff <id> --from V1 --to V2
dcc <noun> verify <id> --version V
dcc <noun> hash <id> --version V
dcc <noun> bump <id> --from V1 --to V2
dcc <noun> validate <id> --version V
dcc <noun> export <id> --version V
dcc <noun> create --environment-id E --application-id A --from-file FILE -m "type(scope): summary"
dcc <noun> edit <id> --from-file FILE -m "..."
dcc <noun> status <id> --target STATUS -m "..."
dcc <noun> rollback <id> --version V --yes -m "..."
dcc <noun> delete <id> --yes -m "..."
dcc <noun> restore <id> --yes -m "..."
```

`standard list` adds `--keyword`, `--category`, `--page`, `--size` and uses the paged
endpoint. `table` adds `ddl <id> [--schema S] [--flyway]` and
`ddl-alter <id> --from V1 --to V2 [--schema S]`. Export formats follow the noun:
standard/interface → `java`, table → `sql`, template → none.

`create` / `edit` take a JSON or YAML object matching the REST request body
(`{"code": "...", "content": {...}}`; `"-"` reads stdin). Scalar flags (`--code`,
`--category`, `--interface-no`, `--template-id`) override top-level fields.

### Audit and drift

```
dcc audit history --entity-type T --entity-id N
dcc audit search  [--entity-type T] [--entity-id N] [--actor A] [--action A]
                  [--from RFC3339] [--to RFC3339] [--page N] [--size N]
dcc audit verify  --entity-type T --entity-id N
dcc drift --environment-id E --application-id A
```

`audit verify` exits 1 when the hash chain is broken. `drift` exits 1 when any snapshot
drifted, so it can gate a pipeline.

### Migration orders

```
dcc migration create --artifact-type T --artifact-id N \
    --source-environment-id E --target-environment-id E -m "..."
dcc migration create --application-id A \
    --source-environment-id E --target-environment-id E [--artifact-types T1,T2] -m "..."
dcc migration list [--target-environment-id E] [--status S]
dcc migration view <id>
dcc migration submit <id> -m "..."
dcc migration approve <id> [--yes] -m "..."
dcc migration reject <id> [--yes] -m "..."
dcc migration execute <id> [--yes] -m "..."
dcc migration rollback <id> [--yes] -m "..."
```

### Environments and applications

```
dcc environment list | view <id> | create --from-file F -m "..." |
                edit <id> --from-file F -m "..." | delete <id> --yes -m "..." |
                restore <id> --yes -m "..."
dcc application list | view <id> | create --from-file F -m "..." |
                edit <id> --from-file F -m "..." | delete <id> --yes -m "..." |
                restore <id> --yes -m "..."
```

### Completion and version

```
dcc completion bash|zsh|fish|powershell
dcc version
```

## Change notes

Every write command requires a change note in GitHub commit-message form. The note is
validated **only in the CLI** and sent as `X-DCC-Change-Reason`; backend columns and APIs
are unchanged.

```
<type>(<scope>): <subject>

<body>

<footer>
```

- `type`: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`,
  `chore`, `revert`.
- `scope` is optional (recommended: `standard`, `table`, `interface`, `template`,
  `migration`, `environment`, `application`).
- `subject` is required, at most 72 characters, and must not end with a period.
- `body` and `footer` are optional. Because HTTP headers are single-line, the client
  flattens body clauses with `; ` and truncates the header at 1024 characters.

Input sources:

```bash
dcc standard edit 7 --from-file standard.yaml -m "feat(standard): add settlement field"
dcc standard edit 7 --from-file standard.yaml -m "feat(standard): add field" -m "Refs: DCC-12"
dcc standard edit 7 --from-file standard.yaml --message-file note.txt
```

With neither flag, an interactive terminal opens `$GIT_EDITOR` / `$VISUAL` / `$EDITOR`
(`vi` by default). Non-interactive runs must pass `-m` or `--message-file`.

Destructive commands (`delete`, `restore`, `rollback`, migration `approve`/`reject`/
`execute`/`rollback`) ask for confirmation; `--yes` skips the prompt, and a non-TTY run
without `--yes` fails rather than guessing.

## Output and exit codes

- Default: human-readable. Lists are aligned tables on a TTY and tab-separated on a pipe.
- `--json` / `--json=fields`, `--jq`, `--template` produce machine output.
- Colour is disabled by `--no-color`, `NO_COLOR`, or a non-TTY stdout.

| Code | Meaning |
| --- | --- |
| 0 | success |
| 1 | API/domain failure, `valid=false`, drift or a broken audit chain |
| 2 | usage error (bad flags or invalid change note) |
| 4 | authentication/authorization failure (HTTP 401/403) |

## API mapping

| CLI | Endpoint |
| --- | --- |
| `list` | `GET /api/<resource>` or `GET /api/standards/page` |
| `view` / `history` / `diff` / `verify` | `GET /api/<resource>/{id}/...` |
| `create` / `edit` / `status` / `rollback` / `delete` / `restore` | `/api/<resource>/...` |
| `hash` / `bump` / `validate` / `export` | `POST /cli/<version>/...` |
| `audit history` / `search` / `verify` | `POST /cli/<version>/audit/...` |
| `drift` | `POST /cli/<version>/drift` |
| `migration *` | `/api/migrations/...` |
| `environment *` / `application *` | `/api/environments/...`, `/api/applications/...` |

## Migration from the Java CLI

| Old | New |
| --- | --- |
| `dcc hash --type T --model-id N --version V` | `dcc <noun> hash N --version V` |
| `dcc diff ... --from V1 --to V2` | `dcc <noun> diff N --from V1 --to V2` |
| `dcc bump ...` | `dcc <noun> bump N --from V1 --to V2` |
| `dcc validate ...` | `dcc <noun> validate N --version V` |
| `dcc export ... --format java\|sql` | `dcc <noun> export N --version V` |
| `dcc ci --env-id E --app-id A` | `dcc drift --environment-id E --application-id A` |
| `dcc audit-history ...` | `dcc audit history ...` |
| `dcc audit-verify ...` | `dcc audit verify ...` |
| `dcc restore --type T --model-id N` | `dcc <noun> restore N --yes -m "..."` |
| `--output json` | `--json` |
| `--server URL` | unchanged, plus `auth login` and `hosts.yml` |

## Development

```bash
cd dcc-cli
make test          # go test ./...
make build         # bin/dcc with version metadata
make lint          # go vet + gofmt check
```

The command tests use `httptest` and the injected factory; no backend is required.
An integration run against a live `dcc-web` is optional:

```bash
DCC_SERVER_URL=http://localhost:8080 DCC_TOKEN=... dcc standard list
```
