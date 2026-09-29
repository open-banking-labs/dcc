package cn.org.openbanking.dcc.generator.validation;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.generator.source.GeneratedSource;
import cn.org.openbanking.dcc.generator.template.TemplateRenderer;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

/**
 * Generates a Java validation class for a data standard, from the
 * {@code validation.java} template: the standard's constraints (required, length,
 * regex) become constants plus a {@code validate} method.
 */
@Component
public class ValidationCodeGenerator {

    private final TemplateRenderer engine;

    public ValidationCodeGenerator(TemplateRenderer engine) {
        this.engine = engine;
    }

    public GeneratedSource generate(String packageName, String className, String code, StandardContent content) {
        Map<String, Object> context = new HashMap<>();
        context.put("packageName", packageName);
        context.put("className", className);
        context.put("code", code);
        context.put("standard", quote(code));
        context.put("required", String.valueOf(content.required()));
        context.put("length", content.length() == null ? "null" : content.length().toString());
        context.put("regex", content.regex() == null ? "null" : quote(content.regex()));
        context.put("requiredMessage", quote(code + " is required"));
        context.put("mismatchMessage", quote(code + " does not match "));
        context.put("lengthMessage", quote(code + " exceeds length "));

        String source = engine.process("validation.java", new Context(Locale.ROOT, context));
        return new GeneratedSource(packageName.replace('.', '/') + "/" + className + ".java", source);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
