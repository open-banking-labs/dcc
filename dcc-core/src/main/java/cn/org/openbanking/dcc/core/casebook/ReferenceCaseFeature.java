package cn.org.openbanking.dcc.core.casebook;

/**
 * What a {@link ReferenceCase} is an example of (ADR 0005 §D7).
 *
 * <p>The first four map onto the artifacts a tenant maintains; {@link #SKELETON} carries the
 * recommended environment/application shape (e.g. DEV / TEST / UAT / PROD plus a starter
 * application) so a newly admitted tenant has a starting point without the platform touching
 * the tenant's instances.
 */
public enum ReferenceCaseFeature {

    /** 数标 example. */
    DATA_STANDARD,

    /** 表结构 example. */
    TABLE_STRUCTURE,

    /** 接口 example. */
    INTERFACE,

    /** 接口模板 example. */
    INTERFACE_TEMPLATE,

    /** Environment/application skeleton example. */
    SKELETON
}
