# dcc-starter

`dcc-starter` is the executable Spring Boot application and the web exposure layer. It calls the business logic in `dcc-api` and owns the main class, HTTP endpoints/DTOs, OpenAPI, profile configuration, Actuator and Flyway startup integration. Keep business logic out of this module; add new exposure layers (web today, RPC/MCP later) that delegate to `dcc-api`.

## Application warm-up

`WarmupApplicationRunner` runs the registered `WarmupTask` beans once the context is
ready. Add a step by implementing `WarmupTask` as a Spring bean; steps run in
`@Order` / `Ordered` order. Everything else is driven by configuration under
`dcc.warmup`:

| Property | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | run the warm-up at all |
| `order` | `0` | position of the warm-up runner among the other `ApplicationRunner`s |
| `async` | `false` | run off the startup thread instead of blocking it |
| `fail-fast` | `true` | on failure, stop the application; `false` logs and keeps going |

```java
@Component
@Order(10)
class LoadReferenceData implements WarmupTask {
    @Override
    public void warmUp() {
        // ...
    }
}
```
