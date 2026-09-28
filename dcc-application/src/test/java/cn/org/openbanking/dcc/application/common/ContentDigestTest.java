package cn.org.openbanking.dcc.application.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class ContentDigestTest {

    private final ContentDigest digest = new ContentDigest(new ObjectMapper());

    @Test
    void canonicalIrIsKeyOrderIndependent() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 2);
        first.put("a", 1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 1);
        second.put("b", 2);

        assertThat(digest.canonical(first)).isEqualTo(digest.canonical(second));
        assertThat(digest.digest(first)).isEqualTo(digest.digest(second)).startsWith("sha256:");
    }

    @Test
    void canonicalIrExcludesVolatileFields() {
        Map<String, Object> withTimestamp = new LinkedHashMap<>();
        withTimestamp.put("name", "acct");
        withTimestamp.put("createdAt", "2026-01-01T00:00:00Z");
        Map<String, Object> withoutTimestamp = new LinkedHashMap<>();
        withoutTimestamp.put("name", "acct");

        assertThat(digest.canonical(withTimestamp)).isEqualTo(digest.canonical(withoutTimestamp));
    }

    @Test
    void differentContentProducesDifferentHash() {
        assertThat(digest.digest(Map.of("name", "a"))).isNotEqualTo(digest.digest(Map.of("name", "b")));
    }

    @Test
    void verifyDetectsTampering() {
        Map<String, Object> content = Map.of("name", "acct");
        String hash = digest.digest(content);
        assertThat(digest.verify(hash, content)).isTrue();
        assertThat(digest.verify(hash, Map.of("name", "tampered"))).isFalse();
    }
}
