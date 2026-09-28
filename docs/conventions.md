# Conventions

- Java packages use `cn.org.openbanking...`.
- Place code in the module that owns its responsibility: persistence/domain in `dcc-core`, business logic in `dcc-application`, shared startup wiring in `dcc-bootstrap`, shared security in `dcc-security`, and transport/bootstrapping in the exposure layers (`dcc-web`, `dcc-mcp`).
- Tests live under the corresponding module's `src/test/java` tree.
- Environment configuration belongs in profile-specific Spring configuration files; secrets belong in environment variables or local ignored files.
- Flyway scripts use `V<yyyyMMddHHmmss>__<verb_led_lower_snake_case>.sql` and must follow the rules in the Flyway naming guide.
- Prefer small, focused changes and preserve existing neighboring style.
