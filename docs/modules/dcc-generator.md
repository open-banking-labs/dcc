# dcc-generator

`dcc-generator` turns the domain models (data standards, table structures, interfaces)
into downstream artifacts: Java validation classes, request/response DTO records,
PostgreSQL DDL / Flyway SQL, OpenAPI documents, and packaged JAR bundles.

All output is rendered from **Thymeleaf templates** (see
[Templating](../templating.md)) under `src/main/resources/templates/`, in `TEXT` mode
so values are not HTML-escaped. Templates can be overridden per deployment via
`dcc.generator.template-dir`.

Type mapping (logical type → SQL / Java / OpenAPI) is configuration-driven and
dialect-aware; the active dialect is `dcc.database.dialect`. See
[Configuration](../configuration.md).

## Key types

- `template/TemplateRenderer` — renders a named template, preferring a user override.
- `template/GeneratorTemplates` / `GeneratorConfiguration` — build the engine/bean.
- `type/TypeMappingStrategy` (+ PostgreSQL / MySQL impls) — dialect type mapping.
- `dto/DtoGenerator`, `validation/ValidationCodeGenerator`, `ddl/TableDdlGenerator`,
  `openapi/OpenApiGenerator`, `bundle/JarBundleGenerator` — the generators.

## Tests

`TemplatingRegressionTest` locks output to `src/test/resources/golden/`;
`TypeMappingStrategyTest` and `GeneratorWiringTest` cover mapping and auto-wiring.
