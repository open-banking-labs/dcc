# dcc-cli Go rewrite — implementation plan

> Status: implemented (P0–P4 complete; P5 verification in progress). See
> "Implementation notes (as built)" for deviations from the original plan.
>
> Decisions taken by the owner:
> 1. Change-note normalisation happens **only at the CLI input layer** — backend
>    `message` / `note` columns and APIs stay unchanged.
> 2. Command layout follows **gh's noun + verb** style.
> 3. Scope is the **full maintenance workflow**, not just the current nine commands.
> 4. gh-style capabilities: `auth`, local hosts config, `--json` / `--jq` / `--template`,
>    shell completion, interactive confirmation.

## 1. Goals and non-goals

### Goals

- Replace the Java `dcc-cli` module with a standalone Go module living in `dcc-cli/`.
- Keep the CLI an **HTTP client only**: no business logic, no direct database access.
- Mirror `cli/cli` (GitHub CLI) conventions: Cobra command tree, `pkg/cmd/<noun>/<verb>`,
  `pkg/cmd/factory`, `api/` client package, `pkg/iostreams`, `internal/` helpers.
- Cover the complete maintenance workflow exposed by `dcc-web`: artifacts, audit,
  migration orders, environments/applications, drift and export.
- Normalise every maintenance note at the CLI boundary as a GitHub-style commit message.
- Provide gh-grade scripting and terminal ergonomics.

### Non-goals

- No backend schema/API changes in this refactor.
- No tenant/platform administration beyond environment and application scope (tenants,
  memberships, role assignments, announcements, casebook stay out of scope for v1 of
  the Go CLI; they can be added later under the same pattern).
- No TUI, no web login flow, no plugin/extension system (gh extensions are out of scope).
- No reimplementation of validation, hashing, version bumping or generation.

## 2. Why direct REST for maintenance, `/cli/v1` for command verbs

The existing `/cli/v1` group is deliberately slim: hash, diff, bump, validate, export,
drift, audit, restore. Full maintenance CRUD (create/update/status/rollback/delete)
already exists under `/api/*` and is used by `dcc-console`. Duplicating it in `/cli/v1`
would create two write paths.

Rule:

| Kind | Endpoint group |
| --- | --- |
| Resource CRUD, history, verify, status, rollback, DDL, migration orders | `/api/*` |
| Command verbs: hash, bump, validate, export, drift | `/cli/<version>/*` |
| Audit history/search/verify | `/cli/<version>/audit/*` |
| Restore | `/api/<resource>/{id}/restore` (resource-scoped) |

The API version segment for `/cli/*` remains configurable (`api_version`, default `v1`).

## 3. Repository layout

`dcc-cli/` becomes a Go module; the Java sources and `pom.xml` are removed.

```
dcc-cli/
├── go.mod                          module github.com/open-banking-labs/dcc/dcc-cli
├── go.sum
├── Makefile                        build / test / lint / fmt / snapshot / install
├── .goreleaser.yaml
├── cmd/dcc/main.go                 process entry: signal handling, Execute()
├── api/
│   ├── client.go                   HTTP transport, auth, error decoding
│   ├── command.go                  typed wrappers for /cli/v1 verbs
│   ├── resources.go                typed wrappers for /api/* maintenance calls
│   └── types.go                    wire DTOs (views, pages, errors)
├── internal/
│   ├── build/build.go              version/date injected via -ldflags
│   ├── config/config.go            hosts.yml read/write, resolution precedence
│   ├── changemessage/changemessage.go
│   ├── jsonfield/jsonfield.go      --json field whitelist + projection
│   ├── jq/jq.go                    --jq via github.com/itchyny/gojq
│   ├── tableprinter/tableprinter.go
│   └── confirmation/confirmation.go
├── pkg/
│   ├── iostreams/iostreams.go      stdin/stdout/stderr, TTY detection, color
│   ├── option/option.go            pointer/optional helpers
│   └── cmd/
│       ├── root/root.go            command tree + global flags
│       ├── factory/factory.go      dependency injection seam for tests
│       ├── auth/{login,logout,status}/
│       ├── standard/{list,view,create,edit,delete,restore,status,rollback,
│       │             history,diff,verify,hash,bump,validate,export}/
│       ├── table/{...}/            same set + ddl, ddl-alter, export
│       ├── interface/{...}/        same set as standard
│       ├── template/{...}/         same as standard minus export
│       ├── audit/{history,search,verify}/
│       ├── migration/{create,list,view,submit,approve,reject,execute,rollback}/
│       ├── environment/{list,view,create,edit,delete,restore}/
│       ├── application/{list,view,create,edit,delete,restore}/
│       ├── drift/drift.go
│       ├── completion/completion.go
│       └── version/version.go
└── docs/schema/                    JSON schemas + examples for --from-file
```

Each leaf command is a package exporting `NewCmdX(f *factory.Factory, runF func(*XOptions) error) *cobra.Command`,
matching gh's testable command pattern.

## 4. Command tree

Global flags (available on every command):

```
--hostname HOST        configured host to use (default: current host)
--server URL           one-shot base URL override (env DCC_SERVER_URL)
--api-version V        /cli path version (default v1)
--json [FIELDS]        JSON output; optional comma-separated field list
--jq EXPR              filter JSON output
--template TEMPLATE    render JSON output with a Go template
--no-color             disable ANSI colour (NO_COLOR also honoured)
-v, --version          print version
```

Write commands additionally accept:

```
-m, --message TEXT     commit-message-formatted note (repeatable)
-F, --message-file P   read the note from a file ("-" = stdin)
--yes                  skip interactive confirmation
```

### Artifact commands

`standard`, `table`, `interface`, `template` share the same verb set; capability
differences are noted.

```
dcc standard list [--environment-id E] [--application-id A] [--keyword K]
                  [--category C] [--status S] [--page N] [--size N]
dcc standard view <id>
dcc standard create --environment-id E --application-id A --from-file FILE
dcc standard edit <id> --from-file FILE
dcc standard status <id> --target STATUS
dcc standard rollback <id> --version V
dcc standard history <id>
dcc standard diff <id> --from V1 --to V2
dcc standard verify <id> --version V
dcc standard hash <id> --version V
dcc standard bump <id> --from V1 --to V2
dcc standard validate <id> --version V
dcc standard export <id> --version V [--format java]     # standard: java only
dcc standard delete <id> --yes
dcc standard restore <id> --yes
```

`table` adds:

```
dcc table ddl <id> [--schema S]
dcc table ddl-alter <id> --from V1 --to V2 [--schema S]
dcc table export <id> --version V [--format sql]         # sql only
```

`interface` mirrors `standard` with java export. `template` omits export and DDL but
supports hash/bump/validate.

`--from-file` accepts JSON or YAML and maps to the existing request bodies:

| Resource | Schema |
| --- | --- |
| standard | `{code, category, content}` (`code` ignored on edit) |
| table | `{code, content}` |
| interface | `{interfaceNo, templateId, content}` |
| template | `{code, content}` |
| environment | `{code, name, description, sortOrder}` |
| application | `{code, name, description}` |
| migration | `{artifactType, artifactId, sourceEnvironmentId, targetEnvironmentId, note}` or `{sourceEnvironmentId, targetEnvironmentId, applicationId, artifactTypes}` |

Simple scalar flags (`--code`, `--name`, `--category`, `--interface-no`, `--template-id`)
override the file. `--from-file -` reads stdin. Edit commands send only the fields the
REST endpoint accepts (`category`+`content`, `content`, etc.).

### Audit and drift

```
dcc audit history --entity-type T --entity-id N
dcc audit search [--entity-type T] [--entity-id N] [--actor A] [--action A]
                 [--from RFC3339] [--to RFC3339] [--page N] [--size N]
dcc audit verify --entity-type T --entity-id N
dcc drift --environment-id E --application-id A     # exits 1 when drift is found
```

### Migration orders

```
dcc migration create --artifact-type T --artifact-id N --source-environment-id E --target-environment-id E
dcc migration create --application-id A --source-environment-id E --target-environment-id E [--artifact-types T1,T2]
dcc migration list [--target-environment-id E] [--status S]
dcc migration view <id>
dcc migration submit <id>
dcc migration approve <id>
dcc migration reject <id>
dcc migration execute <id> --yes
dcc migration rollback <id> --yes
```

### Environments and applications

```
dcc environment list
dcc environment view <id>
dcc environment create --from-file FILE
dcc environment edit <id> --from-file FILE
dcc environment delete <id> --yes
dcc environment restore <id> --yes
dcc application list | view | create | edit | delete | restore
```

### Auth, completion, version

```
dcc auth login [--hostname HOST] [--token TOKEN | --token-stdin]
dcc auth status [--hostname HOST]
dcc auth logout [--hostname HOST] [--all]
dcc completion bash|zsh|fish|powershell
dcc version
```

## 5. Configuration and authentication

### Files

- `$DCC_CONFIG_DIR/hosts.yml`, else `$XDG_CONFIG_HOME/dcc/hosts.yml`, else
  `~/.config/dcc/hosts.yml`.
- File mode `0600`, directory `0700`.
- Tokens are stored in plain text in this file for v1 (documented); `DCC_TOKEN` avoids
  persistence. Keychain integration is a follow-up, not part of this refactor.

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

### Resolution precedence

1. flags: `--server`, `--hostname`, `--api-version`
2. environment: `DCC_SERVER_URL`, `DCC_TOKEN`, `DCC_API_VERSION`,
   `DCC_CONNECT_TIMEOUT_SECONDS`, `DCC_REQUEST_TIMEOUT_SECONDS`, `DCC_HOSTNAME`
3. `hosts.yml`
4. defaults: `http://localhost:8080`, `v1`, 10s connect, 60s request

Environment variables keep working so existing CI jobs do not break. `DCC_TOKEN` wins
over the stored token.

### `auth` behaviour

- `login` validates the token by calling `GET /api/me`, then writes the host entry.
- `login --token-stdin` reads one line from stdin (CI-safe).
- `status` prints host, server URL, token source and `GET /api/me` result.
- `logout` removes one host or `--all`; it never touches `DCC_TOKEN`.

## 6. Change notes as commit messages

Every write command requires a note. The note is validated **only in the CLI** and sent
as the existing `X-DCC-Change-Reason` header, so backend `message` / `note` columns and
APIs are untouched.

### Accepted format

```
<type>(<scope>): <subject>

<body>

<footer>
```

- `type`: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`,
  `chore`, `revert`.
- `scope`: optional; recommended values are the resource nouns (`standard`, `table`,
  `interface`, `template`, `migration`, `environment`, `application`).
- `subject`: required, ≤ 72 characters, no trailing period.
- `body`: optional, separated by one blank line; lines ≤ 100 characters (warning only).
- `footer`: optional, e.g. `Refs: DCC-123`, `BREAKING CHANGE: ...`.

### Input rules

- `-m/--message` may repeat; segments are joined with a blank line, exactly like git.
- `--message-file FILE` (or `-`) supplies the message from a file/stdin.
- With neither flag: if stdin is a TTY, open `$EDITOR`/`$GIT_EDITOR` with a template;
  otherwise fail with exit 2 and a usage hint.
- Validation failure prints the offending line and the expected grammar, exit 2.

### Transport

HTTP headers are single-line. The client normalises the validated message before
sending:

- subject stays first;
- body/footer lines are collapsed into `; `-separated clauses;
- the result is truncated to 1024 characters (the backend column limit) with a warning
  when truncation happens.

The full message is also echoed in `--json` output as `changeMessage` for auditability
in scripts.

## 7. Output, errors and exit codes

### Output

- Default: human-readable. Lists use `tableprinter` (aligned columns); views print
  key/value pairs and pretty-printed content.
- `--json` with no value: complete JSON object/array for the command.
- `--json a,b,c`: whitelisted projection; unknown fields fail and list valid names.
- `--jq` and `--template` operate on the JSON representation and are mutually exclusive
  with each other (but usable with `--json`).
- Colour is disabled when `--no-color`, `NO_COLOR`, or stdout is not a TTY.
- No pager in v1.

### Errors and exit codes

| Code | Meaning |
| --- | --- |
| 0 | success |
| 1 | API/domain failure, validation `valid=false`, drift detected |
| 2 | usage error (bad flags, bad commit message) |
| 4 | authentication/authorization failure (401/403) |

Errors go to stderr prefixed with `dcc: `. The backend error body
`{timestamp,status,error,message}` is unwrapped to its `message`.

## 8. Testing strategy

- Unit tests for `internal/changemessage`, `internal/config`, `internal/jsonfield`,
  `internal/tableprinter`.
- `api/client_test.go` with `httptest.Server`: method, path, query, body, headers
  (`Authorization`, `X-DCC-Change-Reason`), error decoding and timeouts.
- One test per command package using the gh pattern
  `NewCmdX(factory, runF)` with captured `iostreams` and an injected HTTP test server;
  cover happy path, required-flag errors, `--json`, confirmation refusal and exit code.
- Golden help text per top-level noun to catch flag regressions.
- Optional integration suite behind `-tags=integration`, driven by `DCC_SERVER_URL` /
  `DCC_TOKEN` against a running `dcc-web`; skipped by default.
- CI gate: `gofmt -l`, `go vet ./...`, `go test ./...`, `go build ./cmd/dcc`.

## 9. Build, release and repository integration

- `Makefile`: `build`, `test`, `vet`, `fmt`, `lint`, `install`, `snapshot`, `clean`.
- `.goreleaser.yaml`: darwin/linux/windows × amd64/arm64, tar.gz/zip archives,
  checksums, `dcc version` ldflags.
- `.github/workflows/cli.yml`: Go 1.24+ matrix (`ubuntu-latest`, `macos-latest`),
  `go test`, `go vet`, `goreleaser build --snapshot`.
- Root `pom.xml`: remove `<module>dcc-cli</module>`.
- `Dockerfile`: drop `COPY dcc-cli/pom.xml`.
- `.gitignore`: add `dcc-cli/dist/`, `dcc-cli/bin/`.
- Docs updated: `docs/cli.md` (full rewrite), `docs/architecture.md` (module kind),
  `AGENTS.md` (module description and commands), `docs/configuration.md`
  (`DCC_*` precedence), `docs/api.md` (CLI endpoint usage unchanged).

## 10. Phases and acceptance criteria

### P0 — Toolchain and skeleton

- `go.mod` (module `github.com/open-banking-labs/dcc/dcc-cli`, `go 1.24`), Makefile,
  `cmd/dcc/main.go`, root command, factory, iostreams, version, completion.
- Accept: `go build ./cmd/dcc` and `dcc --help` list the noun groups.

### P1 — Config, auth, client, output

- `hosts.yml`, precedence, `auth login/status/logout`, `api.Client` for `/api` and
  `/cli`, table printer, `--json`, `--jq`, `--template`, colour/TTY handling.
- Accept: unit tests green; `auth login` + `auth status` round-trip against a fake
  server; `--json` projections validated.

### P2 — Read-only maintenance commands

- `standard|table|interface|template` `list/view/history/diff/verify/hash/bump/validate`,
  `table ddl/ddl-alter`, resource `export`, `audit *`, `drift`.
- Accept: every command has an httptest-backed test; paths/bodies match the Java
  controllers; `drift` exits 1 on findings.

### P3 — Write commands, notes, confirmations

- `create/edit/status/rollback/delete/restore`, migration order lifecycle,
  environment/application CRUD.
- Commit-message validation, `$EDITOR` fallback, `X-DCC-Change-Reason` transport,
  `--yes` and destructive confirmations.
- Accept: write tests assert the header value and request body; invalid messages exit 2;
  destructive commands refuse without a TTY unless `--yes`.

### P4 — Decommission Java CLI and update docs

- Remove `dcc-cli/pom.xml`, `dcc-cli/src/main/java`, `dcc-cli/target`; remove the Maven
  module and Dockerfile copy; update docs, AGENTS.md, .gitignore; add GoReleaser and
  workflow.
- Accept: `./mvnw -q -DskipTests package` no longer builds `dcc-cli`; `grep -r dcc-cli`
  shows no Java references; Go build/test green from a clean checkout.

### P5 — Final verification

- `gofmt -l .`, `go vet ./...`, `go test ./...`, `go build ./cmd/dcc`.
- Optional integration run against a local `dcc-web` with a real token.
- Manual smoke: login, list standards, view one, create+edit with a commit-message
  note, history, diff, export, drain audit, drift.
- Accept: all commands above succeed and exit codes match section 7.

## 10.1 Implementation notes (as built)

- Commands are grouped **one Go package per noun** (`pkg/cmd/standard`, `pkg/cmd/table`,
  `pkg/cmd/interfaceapi`, ...), not one package per verb. Each verb is a file exporting
  `NewCmdX`. This keeps the tree reviewable without dozens of tiny packages.
- The `interface` command lives in `pkg/cmd/interfaceapi` because `interface` is a Go
  keyword; the user-facing command name remains `interface`.
- `--json` uses pflag's optional-value syntax: bare `--json` prints everything, while
  field selection must be written `--json=id,code` (a space would be parsed as a
  positional argument).
- Change notes are required for every write. Migration orders put the full message in
  `note` (truncated to the 512-character column), while every write sends the flattened
  subject/body as `X-DCC-Change-Reason` (≤1024). Backend schema and APIs are unchanged.
- Tokens are stored as plain text in a 0600 `hosts.yml`; `DCC_TOKEN` and `--token-stdin`
  avoid persistence. Keychain integration remains a follow-up.
- `go.mod` declares `go 1.24.0`; the CI workflow uses Go 1.24.x.
- Release assets are produced by GoReleaser (darwin/linux/windows × amd64/arm64) from
  `.goreleaser.yaml`; `.github/workflows/cli.yml` runs gofmt/vet/test/build and the
  tagged release.
- `dcc-cli` is no longer a Maven module: the root `pom.xml` and `Dockerfile` no longer
  reference it, and the Java sources, POM and `target/` directory were removed.

## 11. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Complex `content` payloads are hard to pass as flags | `--from-file` is the primary path; scalar flags only override top-level fields. Ship schema examples and validate unknown keys client-side. |
| `X-DCC-Change-Reason` is single-line | Normalise in one place (`internal/changemessage`) and test the exact header. Backend unchanged. |
| Permission differences across roles | Map 401/403 to actionable messages; `auth status` calls `/api/me` and prints the subject. |
| Full CRUD expands test surface | Table-driven httptest per command, generated from the same option structs; phase gates keep each PR reviewable. |
| Stored token in plain text | 0600 file, documented; `DCC_TOKEN` recommended for CI; keychain as follow-up. |
| Go toolchain absent on developer machines | Document `go 1.24+`; CI installs Go; `Makefile` fails fast with a clear message. |
