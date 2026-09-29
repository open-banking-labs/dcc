package cn.org.openbanking.dcc.generator.template;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

/**
 * Builds the renderer shared by every generator.
 *
 * <p>Templates run in {@link TemplateMode#TEXT}: there is no markup to parse, so
 * values are emitted verbatim and characters like {@code <}, {@code >}, {@code &}
 * and {@code "} are <em>not</em> HTML-escaped — exactly what generated code, SQL and
 * prompts require.
 *
 * <p>The built-in templates live on the classpath under {@code templates/}. When
 * {@code dcc.generator.template-dir} points elsewhere (a filesystem directory or a
 * classpath location), a template found there wins over the built-in default; a
 * template it does not provide falls back to the built-in one.
 */
public final class GeneratorTemplates {

    /** Classpath location of the built-in templates. */
    public static final String BUILTIN_PREFIX = "templates/";

    /** File extension of the template files. */
    public static final String TEMPLATE_SUFFIX = ".tpl";

    private static final String DEFAULT_TEMPLATE_DIR = "classpath:/templates/";

    private GeneratorTemplates() {
    }

    /** Creates a renderer from the given configuration. */
    public static TemplateRenderer create(GeneratorProperties properties) {
        TemplateEngine builtin = engineWith(classpathResolver(BUILTIN_PREFIX));
        String dir = properties == null ? null : properties.getTemplateDir();
        if (dir == null || dir.isBlank() || DEFAULT_TEMPLATE_DIR.equals(dir)) {
            return new TemplateRenderer(builtin, null, name -> false);
        }
        String value = dir.trim();
        if (value.startsWith("file:")) {
            return fileOverride(builtin, normalizeFile(value.substring("file:".length())));
        }
        if (value.startsWith("classpath:")) {
            return classpathOverride(builtin, normalizeClasspath(value.substring("classpath:".length())));
        }
        // No scheme: treat as a filesystem directory so a plain path also overrides.
        return fileOverride(builtin, normalizeFile(value));
    }

    /** Creates a renderer over the built-in defaults. */
    public static TemplateRenderer create() {
        return create(new GeneratorProperties());
    }

    private static TemplateRenderer fileOverride(TemplateEngine builtin, String prefix) {
        TemplateEngine override = engineWith(fileResolver(prefix));
        Path dir = Path.of(prefix);
        return new TemplateRenderer(builtin, override,
                name -> Files.exists(dir.resolve(name + TEMPLATE_SUFFIX)));
    }

    private static TemplateRenderer classpathOverride(TemplateEngine builtin, String prefix) {
        TemplateEngine override = engineWith(classpathResolver(prefix));
        ClassLoader classLoader = GeneratorTemplates.class.getClassLoader();
        return new TemplateRenderer(builtin, override,
                name -> classLoader.getResource(prefix + name + TEMPLATE_SUFFIX) != null);
    }

    private static TemplateEngine engineWith(ITemplateResolver resolver) {
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolvers(Set.of(resolver));
        return engine;
    }

    private static ClassLoaderTemplateResolver classpathResolver(String prefix) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(prefix);
        resolver.setSuffix(TEMPLATE_SUFFIX);
        resolver.setForceSuffix(true);
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        return resolver;
    }

    private static FileTemplateResolver fileResolver(String prefix) {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix(prefix);
        resolver.setSuffix(TEMPLATE_SUFFIX);
        resolver.setForceSuffix(true);
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        return resolver;
    }

    /** Makes a classpath prefix relative to the classpath root and trailing-slashed. */
    private static String normalizeClasspath(String path) {
        String trimmed = path.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    /** Guarantees a trailing slash without touching the leading one (paths may be absolute). */
    private static String normalizeFile(String path) {
        String trimmed = path.trim();
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }
}
