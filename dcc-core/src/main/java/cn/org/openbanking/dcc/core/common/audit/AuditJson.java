package cn.org.openbanking.dcc.core.common.audit;

import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * A tiny, dependency-free, deterministic JSON writer for audit payloads.
 *
 * <p>The audit snapshots are built as ordered {@code Map<String, Object>} trees whose
 * leaves are the scalar entity fields (String / Number / Boolean / enum / Instant), so a
 * full JSON library is unnecessary here and would add a Jackson dependency to
 * {@code dcc-core} (which deliberately has none). Keys keep their insertion order, which
 * {@link AuditSnapshot} sorts, so the same row always serialises to the same bytes.
 */
final class AuditJson {

    private AuditJson() {
    }

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            writeString(out, s);
        } else if (value instanceof Boolean || value instanceof Number) {
            out.append(value);
        } else if (value instanceof Enum<?> e) {
            writeString(out, e.name());
        } else if (value instanceof Instant i) {
            writeString(out, i.toString());
        } else if (value instanceof Map<?, ?> map) {
            writeObject(out, map);
        } else if (value instanceof Collection<?> collection) {
            writeArray(out, collection);
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map) {
        out.append('{');
        Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
        boolean first = true;
        while (it.hasNext()) {
            Map.Entry<?, ?> entry = it.next();
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(out, String.valueOf(entry.getKey()));
            out.append(':');
            writeValue(out, entry.getValue());
        }
        out.append('}');
    }

    private static void writeArray(StringBuilder out, Collection<?> values) {
        out.append('[');
        Iterator<?> it = values.iterator();
        boolean first = true;
        while (it.hasNext()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeValue(out, it.next());
        }
        out.append(']');
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
