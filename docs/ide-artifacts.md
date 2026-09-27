# IDE and tool-generated artifacts

Some directories and files in a working copy are produced by editors, IDEs and
assistants rather than authored as part of the project. They are machine-specific,
disposable and noisy in diffs, so they stay out of version control. This page
records the rule and the patterns that implement it, so a new tool can be
classified the same way instead of being decided case by case.

## The rule

Ignore an artifact when **all** of these hold:

- it is generated or maintained by a local tool, not authored as part of the project;
- it is specific to one machine or one developer's session;
- the project builds, runs and tests without it.

Keep — do **not** ignore — anything the team needs to check out to get a consistent
result: shared rule and memory files, formatter/linter/test configuration, and,
where the team has chosen to standardise on it, editor settings such as
`.vscode/settings.json`.

The patterns live in [`.gitignore`](../.gitignore). Stage with explicit paths
(`git add <path>`); avoid `git add -A` / `git add .`, which is how these artifacts
get committed by accident.

## Editor and IDE directories

| Tool | Ignored pattern(s) | Why |
| --- | --- | --- |
| Eclipse / Spring Tool Suite | `.apt_generated`, `.classpath`, `.factorypath`, `.project`, `.settings`, `.springBeans`, `.sts4-cache` | Per-workspace project metadata, regenerated on import |
| IntelliJ IDEA | `.idea`, `*.iws`, `*.iml`, `*.ipr` | Per-developer module and workspace metadata |
| NetBeans | `/nbproject/private/`, `/nbbuild/`, `/nbdist/`, `/.nb-gradle/` | Local build and project cache |
| VS Code | `.vscode/` | Editor settings; this project does not standardise on them |
| Zed | `.zed/` | Project-scoped editor settings (`settings.json`, `keymap.json`) |

Several of these directories mix disposable and shareable files — IntelliJ keeps
`workspace.xml` (local) next to `codeStyles/` (shareable); VS Code keeps
`settings.json` (shareable) next to local state. This project ignores the whole
directory for simplicity. If the team later standardises on one editor, narrow the
pattern (`!.vscode/settings.json`) instead of ignoring it wholesale.

## AI coding assistants

Assistants write local session state into the working copy. Ignore the local state;
keep the shared rule files.

| Tool | Ignored | Kept (committed) |
| --- | --- | --- |
| Claude Code | `.claude/settings.local.json`, `.claude/worktrees/` | `.claude/settings.json`, `CLAUDE.md` |
| Aider | `.aider*` | — |
| Reasonix Code | `.reasonix/` | — |
| Cline | — (session state lives in the IDE's global storage) | `.clinerules/` |
| Codex | — (config lives under `~/.codex/`) | `AGENTS.md` |
| Cursor | — | `.cursor/rules/`, `.cursorrules` |
| GitHub Copilot | — | `.github/copilot-instructions.md` |

Claude Code also adds `**/.claude/settings.local.json` to the machine-level git
excludes (`core.excludesFile`) the first time it writes the file; the entry in this
project's `.gitignore` is a backstop for contributors who do not have that setting.

## Adding a new tool

1. Check the artifact against the rule above. If it does not qualify, do not ignore it.
2. Add the narrowest pattern that matches — a specific directory over a broad glob.
3. Group it under a `### <Tool> ###` heading or a commented section in `.gitignore`.
4. Add a row to the tables here so the next person does not re-derive the decision.
