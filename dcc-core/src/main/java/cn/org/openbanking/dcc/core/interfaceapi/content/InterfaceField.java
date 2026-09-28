package cn.org.openbanking.dcc.core.interfaceapi.content;

import java.util.List;

/**
 * A field of a business interface input/output. It may reference a data standard
 * (so the same standard can be bound under several field names, e.g. {@code acctno}
 * and {@code acctno1}) or be inline; nested structures are expressed with
 * {@link #children}.
 */
public record InterfaceField(
        String name,
        Long standardId,
        String standardCode,
        String dataType,
        Integer length,
        Integer scale,
        boolean required,
        boolean list,
        List<InterfaceField> children) {

    public InterfaceField {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
