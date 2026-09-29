# Contributing

## Before You Change Code

Read `README.md`, `AGENTS.md`, the relevant module documentation under `docs/modules/`, and any applicable Flyway guidance under `docs/flyway/`.

## Development Workflow

1. Create a focused branch or change set.
2. Keep edits within the owning module and avoid unrelated formatting changes.
3. Add or update tests next to the changed module.
4. Run `./mvnw test`; for cross-module changes run `./mvnw clean verify`. The `dcc-web` / `dcc-mcp` smoke tests need PostgreSQL and the `dev` profile - see [docs/setup.md](docs/setup.md).
5. Review `git diff` and `git status` before committing.

## Good first issues

New to the codebase? These are self-contained, well-tested areas that don't need deep
background — good places to start:

- **Templates** — adjust or add a file under `dcc-generator/src/main/resources/templates/`
  or a prompt under `dcc-mcp/src/main/resources/mcp/prompts/`. `TemplatingRegressionTest`
  shows how to change output safely (golden fixtures + a non-escaping check).
- **Type mappings** — add a dialect's overrides under `dcc.type-mapping.dialects.*`
  (see `TypeMappingStrategyTest` and `docs/configuration.md`).
- **Configuration** — every `dcc.*` property is documented in `docs/configuration.md`;
  adding a default plus a row there is a small, high-value change.
- **Docs** — improve `docs/quick-start.md`, `docs/concepts.md` or `docs/api.md`.

Pair every change with a test next to the code it touches (see the existing `*Test` classes).

## Style

- Use Java 17 and existing Spring Boot conventions.
- Preserve the `cn.org.openbanking` package namespace.
- Match neighboring code for naming, Lombok usage and test style.
- Do not commit credentials, local environment files, logs or build output.

## Database Changes

Use a new Flyway migration following `docs/flyway/script-naming-convention.md`. Never edit an applied migration. For rollback or production recovery, follow `docs/flyway/rollback-operations-manual.md`.

## Commits

Use a concise imperative subject that describes the behavior change. Keep each commit reviewable and include documentation updates when public behavior or operational steps change.
