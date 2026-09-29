package cn.org.openbanking.dcc.mcp.prompt;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.org.openbanking.dcc.generator.template.TemplateRenderer;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

/**
 * Renders MCP prompt templates, resolving a locale-specific variant when present.
 *
 * <p>A prompt named {@code review-change} is looked up as {@code review-change.<lang>.md.tpl}
 * for the requested language, then {@code review-change.<default-locale>.md.tpl}, then
 * {@code review-change.md.tpl}. Variable substitution uses the standard Thymeleaf
 * {@code [(${...})]} syntax and is <em>not</em> HTML-escaped (TEXT mode), so prompts
 * can carry code and special characters verbatim.
 */
@Component
public class PromptCatalog {

    private final TemplateRenderer renderer;
    private final PromptProperties properties;

    public PromptCatalog(TemplateRenderer renderer, PromptProperties properties) {
        this.renderer = renderer;
        this.properties = properties;
    }

    /** Renders {@code name} for the given locale (may be {@code null}) with the model. */
    public String render(String name, Locale locale, Map<String, Object> model) {
        Context context = new Context(locale != null ? locale : Locale.ROOT);
        context.setVariables(model == null ? Map.of() : new HashMap<>(model));
        for (String candidate : candidates(name, locale)) {
            if (renderer.exists(candidate)) {
                return renderer.process(candidate, context);
            }
        }
        throw new IllegalArgumentException(
                "No prompt template for '" + name + "' (locale=" + locale + ", dir=" + properties.getDir() + ")");
    }

    /** Renders {@code name} with the default locale. */
    public String render(String name, Map<String, Object> model) {
        return render(name, null, model);
    }

    private List<String> candidates(String name, Locale locale) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (locale != null && locale.getLanguage() != null && !locale.getLanguage().isBlank()) {
            names.add(name + "." + locale.getLanguage());
        }
        if (properties.getDefaultLocale() != null && !properties.getDefaultLocale().isBlank()) {
            names.add(name + "." + properties.getDefaultLocale());
        }
        names.add(name);
        return List.copyOf(names);
    }
}
