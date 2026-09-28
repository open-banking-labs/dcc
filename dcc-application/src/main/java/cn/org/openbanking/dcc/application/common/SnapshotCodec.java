package cn.org.openbanking.dcc.application.common;

import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

/**
 * Serialises versioned content snapshots (table/interface/template) to and from the
 * JSON documents stored on version records. Wraps the application's single
 * {@link ObjectMapper} bean so the JSON format is configured in one place.
 */
@Component
public class SnapshotCodec {

    private final ObjectMapper objectMapper;

    public SnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("cannot serialise snapshot to JSON", ex);
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("cannot deserialise snapshot JSON to " + type.getSimpleName(), ex);
        }
    }
}
