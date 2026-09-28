package cn.org.openbanking.dcc.application.standard;

import cn.org.openbanking.dcc.core.standard.StandardContent;

/**
 * A data standard in a portable, protocol-neutral shape used by import/export.
 * The exposure layers serialise it to/from JSON; the use-case layer never touches
 * the wire format.
 */
public record StandardExportItem(String code, String category, StandardContent content) {
}
