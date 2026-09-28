package cn.org.openbanking.dcc.generator.source;

/** A single generated text artifact (e.g. a Java source file) with its relative path. */
public record GeneratedSource(String fileName, String content) {
}
