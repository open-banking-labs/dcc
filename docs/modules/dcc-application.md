# dcc-application

`dcc-application` holds the main business logic (the application / use-case layer). Keep it protocol-agnostic - it must not depend on HTTP, RPC or other transport types - so it is reused unchanged when a new exposure layer is added. It may use `dcc-core` for persistence and shared primitives.
