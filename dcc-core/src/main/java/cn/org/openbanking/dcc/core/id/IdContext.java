package cn.org.openbanking.dcc.core.id;

import java.time.Instant;

/**
 * Values available while assembling one identifier.
 *
 * @param type          type segment ({@code S}, {@code I}, {@code M}, ...)
 * @param tenantId      numeric tenant id (the 3-digit tenant segment)
 * @param applicationNo per-tenant application number (the 3-digit application segment)
 * @param sequenceValue value allocated from the rule's sequence source
 * @param now           reference time for {@code DATE} parts
 */
public record IdContext(
        Character type,
        Long tenantId,
        Integer applicationNo,
        Long sequenceValue,
        Instant now) {
}
