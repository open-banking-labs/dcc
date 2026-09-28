package cn.org.openbanking.dcc.generator.ddl;

/**
 * Result of generating SQL for a table structure: the plain DDL, and a ready-to-use
 * Flyway migration (file name + script content) following the project's
 * {@code V<yyyyMMddHHmmss>__<verb>.sql} convention.
 */
public record TableDdl(String dialect, String ddl, String flywayFileName, String flywayScript) {
}
