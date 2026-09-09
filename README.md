
# DCC — Data Contract Center

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Status](https://img.shields.io/badge/Status-Alpha-orange.svg)]()

A lightweight data model management layer for financial systems.

---

## What this is

DCC is a tool that helps teams define, version, and share data models across services. It generates code and SQL from a central model definition, and exposes that model to AI coding assistants via MCP.

**It is not a data catalog. It does not store data.** It stores metadata about data — field definitions, table structures, indexes, and shard keys.

---

## Current status

**Alpha.** This project is in early development. APIs will change. Documentation is incomplete. Use with caution.

We are building this to solve real problems in open banking infrastructure. If that sounds relevant to you, feel free to explore.


---

## Key features

- Field standard library
- Table structure management with versioning
- Java entity + Repository code generation
- Flyway SQL generation
- Maven JAR publishing (via Nexus)
- MCP server for AI assistants

---

## Tech stack

- Java 17 / Spring Boot 3
- Vue 3 / Element Plus
- PostgreSQL (metadata storage)
- Redis (session/cache)
- Flyway (schema migration)

---

## Contributing

This is early. We are not actively seeking contributions yet.

But if you see something broken or have a question, feel free to open an issue or discussion. We'll respond.

---

## License

Apache License 2.0. See [LICENSE](LICENSE).

---

## Contact

- Issues / Discussions: GitHub
- Email: hi@openbanking.org.cn

---

**DCC — models as code.**

