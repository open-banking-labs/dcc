package cn.org.openbanking.dcc.generator.template;

import java.util.function.Predicate;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

/**
 * Renders a named template, preferring a user-supplied override directory over the
 * built-in templates. The override is applied when the named template actually
 * exists in the configured location, which keeps the "user &gt; built-in" rule
 * deterministic regardless of the engine's resolver chaining.
 */
public final class TemplateRenderer {

    private final TemplateEngine builtin;
    private final String builtinPrefix;
    private final String suffix;
    private final TemplateEngine override;
    private final Predicate<String> overrideExists;

    TemplateRenderer(TemplateEngine builtin, String builtinPrefix, String suffix,
            TemplateEngine override, Predicate<String> overrideExists) {
        this.builtin = builtin;
        this.builtinPrefix = builtinPrefix;
        this.suffix = suffix;
        this.override = override;
        this.overrideExists = overrideExists;
    }

    /** Renders the named template with the given context, honouring overrides. */
    public String process(String templateName, IContext context) {
        if (override != null && overrideExists.test(templateName)) {
            return override.process(templateName, context);
        }
        return builtin.process(templateName, context);
    }

    /** @return whether the named template is available (override or built-in). */
    public boolean exists(String templateName) {
        if (override != null && overrideExists.test(templateName)) {
            return true;
        }
        ClassLoader classLoader = GeneratorTemplates.class.getClassLoader();
        return classLoader.getResource(builtinPrefix + templateName + suffix) != null;
    }
}
