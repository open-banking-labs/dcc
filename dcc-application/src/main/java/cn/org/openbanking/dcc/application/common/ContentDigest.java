package cn.org.openbanking.dcc.application.common;

import java.util.Set;
import java.util.TreeSet;

import cn.org.openbanking.dcc.core.version.ContentHash;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

/**
 * Compiles an artifact's semantic body into its canonical <b>intermediate
 * representation (IR)</b> and fingerprints it.
 *
 * <p>The IR is deterministic: object keys are sorted, volatile fields (timestamps,
 * authors) are excluded, and array order is preserved (it is semantic). The same
 * source model therefore yields a byte-identical IR, and hence the same content
 * hash, regardless of field declaration or key ordering.
 */
@Component
public class ContentDigest {

    /** Fields that are volatile / non-semantic and excluded from the IR. */
    private static final Set<String> VOLATILE_FIELDS = Set.of(
            "createdAt", "updatedAt", "createdBy", "updatedBy",
            "created_at", "updated_at", "created_by", "updated_by");

    private final ObjectMapper objectMapper;

    public ContentDigest(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** @return the canonical IR (key-sorted, volatile-free) of a content body. */
    public String canonical(Object content) {
        try {
            JsonNode tree = objectMapper.readTree(objectMapper.writeValueAsString(content));
            return objectMapper.writeValueAsString(normalize(tree));
        } catch (RuntimeException ex) {
            throw new IllegalStateException("cannot canonicalise content into an IR", ex);
        }
    }

    /** @return the {@code sha256:...} content hash (identity) of the content. */
    public String digest(Object content) {
        return ContentHash.sha256(canonical(content));
    }

    /** @return true when the content still matches the recorded hash (integrity). */
    public boolean verify(String expectedHash, Object content) {
        return ContentHash.matches(expectedHash, canonical(content));
    }

    private JsonNode normalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode normalised = objectMapper.createObjectNode();
            for (String key : new TreeSet<>(node.propertyNames())) { // sorted keys
                if (VOLATILE_FIELDS.contains(key)) {
                    continue; // exclude volatile fields
                }
                normalised.set(key, normalize(node.get(key)));
            }
            return normalised;
        }
        if (node.isArray()) {
            ArrayNode normalised = objectMapper.createArrayNode();
            for (int i = 0; i < node.size(); i++) {
                normalised.add(normalize(node.get(i))); // array order is semantic
            }
            return normalised;
        }
        return node;
    }
}
