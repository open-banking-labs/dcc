# Conventions

- Java packages use `cn.org.openbanking...`.
- Place code in the module that owns its responsibility: persistence/domain in `dcc-core`, HTTP/API in `dcc-api`, startup and runtime wiring in `dcc-starter`.
- Tests live under the corresponding module's `src/test/java` tree.
- Environment configuration belongs in profile-specific Spring configuration files; secrets belong in environment variables or local ignored files.
- Flyway scripts use `V<yyyyMMddHHmmss>__<verb_led_lower_snake_case>.sql` and must follow the rules in the Flyway naming guide.
- Prefer small, focused changes and preserve existing neighboring style.
