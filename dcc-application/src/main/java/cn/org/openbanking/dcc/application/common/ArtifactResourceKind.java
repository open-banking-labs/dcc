package cn.org.openbanking.dcc.application.common;

/**
 * The kind of resource whose operating surface is being resolved before authorization
 * (ADR 0005, follow-up on ADR 0004's "scoped grants are inert on opaque-ID endpoints").
 */
public enum ArtifactResourceKind {

    /** 数标. */
    DATA_STANDARD,

    /** 表结构. */
    TABLE_STRUCTURE,

    /** 接口. */
    INTERFACE,

    /** 接口模板. */
    INTERFACE_TEMPLATE,

    /** 迁移单 — the surface is the <em>target</em> environment, not the artifact's own. */
    MIGRATION_ORDER
}
