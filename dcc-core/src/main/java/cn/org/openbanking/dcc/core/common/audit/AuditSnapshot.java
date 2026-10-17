package cn.org.openbanking.dcc.core.common.audit;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.Transient;

import org.hibernate.Hibernate;

/**
 * Builds the before/after images the audit trail stores.
 *
 * <p>Every DCC entity is a flat set of scalar columns (there are no JPA associations),
 * so a row can be snapshotted by reflecting over its fields. Declared fields are visited
 * across the whole class hierarchy and sorted by name, which makes the resulting JSON
 * deterministic and the {@link #changes} diff meaningful.
 */
public final class AuditSnapshot {

    private AuditSnapshot() {
    }

    /** @return an ordered, JSON-able image of {@code entity}'s persistent fields. */
    public static Map<String, Object> capture(Object entity) {
        Class<?> type = Hibernate.getClass(entity);
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()
                        || field.isAnnotationPresent(Transient.class)) {
                    continue;
                }
                fields.add(field);
            }
        }
        fields.sort(Comparator.comparing(Field::getName));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                snapshot.put(field.getName(), normalise(field.get(entity)));
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("cannot read audit field " + field.getName()
                        + " on " + type.getSimpleName(), ex);
            }
        }
        return snapshot;
    }

    /**
     * @return the fields whose value differs between {@code before} and {@code after},
     *         carrying the new value; an added field counts as changed and a removed
     *         field is reported with a {@code null} value.
     */
    public static Map<String, Object> changes(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changed = new LinkedHashMap<>();
        Set<String> keys = new LinkedHashSet<>();
        if (before != null) {
            keys.addAll(before.keySet());
        }
        if (after != null) {
            keys.addAll(after.keySet());
        }
        for (String key : keys) {
            Object previous = before == null ? null : before.get(key);
            Object current = after == null ? null : after.get(key);
            if (!Objects.equals(previous, current)) {
                changed.put(key, current);
            }
        }
        return changed;
    }

    private static Object normalise(Object value) {
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        return value;
    }
}
