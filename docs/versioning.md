# Versioning model (three axes)

DCC versioning is deliberately split into three independent axes. They answer
different questions and must not be conflated.

| Axis | Question | Mechanism | Analogy |
| --- | --- | --- | --- |
| **Content hash** | Are two models semantically identical? | SHA-256 over the canonical IR | The model's ID card |
| **Semantic version** | Is this change compatible for downstream? | SemVer (major.minor.patch) | A library's version number |
| **History / lineage** | Who changed what, when and why? | Git-like DAG (parent_hash, author, message) | Git history |

## 1. Canonical IR + content hash (identity & integrity)

Every artifact content body is compiled into a canonical **intermediate
representation (IR)** — a deterministic JSON document (`ContentDigest`):

- object keys are **sorted**;
- **volatile fields** (`createdAt`, `updatedAt`, `createdBy`, `updatedBy`) are excluded;
- array order is **preserved** (it is semantic — column/section order matters).

Because the IR is deterministic, the same source model yields a byte-identical IR
and therefore the same `sha256:…` **content hash**, regardless of key/field ordering.

The hash is:
- **identity** — identical content ⇒ identical hash ⇒ an update is a no-op (no new version record);
- **integrity** — recomputing the hash of a stored snapshot and comparing it to the
  recorded `content_hash` detects tampering (`…/versions/{v}/verify`, `/api/versioning/drift`);
- **cache key / downstream reference** — refer to a model as `model@sha256:abc…`.

## 2. Semantic version (compatibility promise)

SemVer is the promise attached to a *hash transition*, assigned by a field-level
diff, not by the hash itself. The project uses a conservative, **major-biased** rule:

| Level | Typical meaning (advisory) |
| --- | --- |
| `patch` | documentation / description / comment / example changes only |
| `minor` | additive: new column/field/section/index; nothing existing renamed, retyped or reordered |
| `major` | rename, type/constraint change, removal, reorder — anything that can break downstream SQL/code |

Exposed as an **advisory** suggestion (`dcc bump`); the maintainer confirms the final
number. Its value is in review: it warns "this change is a breaking upgrade" before it
ships.

## 3. History / lineage (Git-like)

Each version carries `parent_hash` (its predecessor's `content_hash`), `author` and
`message`, plus a timestamp — a commit-like DAG inside DCC. `content_hash` is the node
id, `parent_hash` the parent edge.

## `dcc hash / diff / bump` commands

| Command | REST | MCP |
| --- | --- | --- |
| hash (stored version) | `GET /api/versioning/{type}/{id}/hash?version=` | `dcc-hash` |
| hash (raw content) | `POST /api/versioning/{type}/hash` | — |
| diff | `GET /api/versioning/{type}/{id}/diff?from=&to=` | `dcc-diff` |
| bump (advisory) | `GET /api/versioning/{type}/{id}/bump?from=&to=` | `dcc-bump` |
| drift | `GET /api/versioning/drift?environmentId=&applicationId=` | — |

`{type}` ∈ `DATA_STANDARD | TABLE_STRUCTURE | INTERFACE | INTERFACE_TEMPLATE`.

## Flyway SQL: model version vs migration version

Generated Flyway scripts embed the source model's hash in the header:

```sql
-- generated from model@sha256:abc… (account)
CREATE TABLE "public"."account" ( ... );
```

- **model version** — semantic version (e.g. `1.2.0`), owned by DCC.
- **migration version** — monotonically increasing `V<yyyyMMddHHmmss>__…`, owned by Flyway.

Migrations are **immutable**: a model change produces a *new incremental* migration
(compare, never overwrite). Regenerating an already-applied file would fail Flyway's
checksum validation — the same "immutable once applied" rule as
[docs/flyway/rollback-operations-manual.md](flyway/rollback-operations-manual.md).

## The three axes are not interchangeable

- A hash change does **not** imply a SemVer change (and vice versa: a SemVer bump is a
  promise, not a fingerprint).
- Git history is external lineage; the in-DCC DAG is the authoritative per-version
  provenance.
