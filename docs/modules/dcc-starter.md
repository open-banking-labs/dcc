# dcc-starter

`dcc-starter` is the executable Spring Boot application for the REST exposure layer. It calls the business logic in `dcc-api` and owns the main class, HTTP endpoints/DTOs, OpenAPI, profile configuration, Actuator and Flyway startup integration. It serves the separate front-end web UI. Keep business logic out of this module; other exposure layers (such as `dcc-mcp`) sit beside it and delegate to `dcc-api`.

## Application warm-up

`WarmupApplicationRunner` runs the warm-up steps once the context is ready. Add a
step by implementing `WarmupTask` as a Spring bean, then list its `name()` in
`dcc.warmup.steps` - that ordered list is what defines the run order, and only the
listed steps run.

| Property | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | run the warm-up at all |
| `steps` | `[]` | ordered list of `WarmupTask` names to run; the list order is the run order |
| `async` | `false` | run off the startup thread instead of blocking it |
| `fail-fast` | `true` | on failure, shut the application down and exit the process; `false` logs and keeps going |

```yaml
dcc:
  warmup:
    steps:
      - load-reference-data   # runs first
      - warm-cache            # runs next
```

```java
@Component
class LoadReferenceData implements WarmupTask {
    @Override
    public String name() {
        return "load-reference-data"; // matches dcc.warmup.steps
    }

    @Override
    public void warmUp() {
        // ...
    }
}
```
