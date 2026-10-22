package cn.org.openbanking.dcc.core.id;

/** The kinds of segments an {@link IdRule} can assemble. */
public enum PartType {
    LITERAL,
    TYPE,
    TENANT,
    APPLICATION,
    SEQUENCE,
    DATE
}
