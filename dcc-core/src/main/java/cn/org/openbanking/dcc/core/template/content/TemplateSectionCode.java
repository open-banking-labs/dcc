package cn.org.openbanking.dcc.core.template.content;

/**
 * The fixed layers of an interface template.
 *
 * <p>Head is split into protocol / gateway / routing / business headers; Body is the
 * business data body; Trailer carries the validation trailer.
 */
public enum TemplateSectionCode {

    /** 协议头 - protocol header. */
    HEAD_PROTOCOL,
    /** 网关头 - gateway header. */
    HEAD_GATEWAY,
    /** 路由头 - routing / discovery header. */
    HEAD_ROUTE,
    /** 业务头 - business header. */
    HEAD_BUSINESS,
    /** 数据体 - business data body. */
    BODY,
    /** 尾部 - validation trailer. */
    TRAILER;

    /** The coarse layer (HEAD / BODY / TRAILER) this section belongs to. */
    public String layer() {
        return switch (this) {
            case HEAD_PROTOCOL, HEAD_GATEWAY, HEAD_ROUTE, HEAD_BUSINESS -> "HEAD";
            case BODY -> "BODY";
            case TRAILER -> "TRAILER";
        };
    }
}
