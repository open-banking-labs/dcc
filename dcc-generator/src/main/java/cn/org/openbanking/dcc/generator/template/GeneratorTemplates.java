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
 * Builds the standalone {@link TemplateRenderer} used by the generators — and, via
 * {@link #create(String, String, String)}, by other templated text such as the MCP
 * prompts, so both share one engine configuration.
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

    private static final String CLASSPATH_SCHEME = "classpath:";
    private static final String FILE_SCHEME = "file:";

    private GeneratorTemplates() {
    }

    /** Creates a renderer for the generator templates from the given configuration. */
    public static TemplateRenderer create(GeneratorProperties properties) {
        String dir = properties == null ? null : properties.getTemplateDir();
        return create(BUILTIN_PREFIX, TEMPLATE_SUFFIX, dir);
    }

    /** Creates a renderer over the built-in generator templates. */
    public static TemplateRenderer create() {
        return create(new GeneratorProperties());
    }

    /**
     * Creates a renderer over a given classpath prefix/suffix, with an optional
     * override location. The override is a Spring-style resource location
     * ({@code classpath:/...}, {@code file:/...}) or a plain filesystem directory.
     *
     * @param builtinPrefix classpath prefix of the built-in templates, e.g. {@code templates/}
     * @param suffix        file suffix, e.g. {@code .tpl}
     * @param overrideLocation user directory; blank or the built-in location disables it
     */
    public static TemplateRenderer create(String builtinPrefix, String suffix, String overrideLocation) {
        TemplateEngine builtin = engineWith(classpathResolver(builtinPrefix, suffix));
        String defaultLocation = CLASSPATH_SCHEME + "/" + builtinPrefix;
        if (overrideLocation == null || overrideLocation.isBlank()
                || overrideLocation.trim().equals(defaultLocation)) {
            return new TemplateRenderer(builtin, builtinPrefix, suffix, null, name -> false);
        }
        String value = overrideLocation.trim();
        if (value.startsWith(FILE_SCHEME)) {
            return fileOverride(builtin, builtinPrefix, normalizeFile(value.substring(FILE_SCHEME.length())), suffix);
        }
        if (value.startsWith(CLASSPATH_SCHEME)) {
            return classpathOverride(builtin, builtinPrefix,
                    normalizeClasspath(value.substring(CLASSPATH_SCHEME.length())), suffix);
        }
        // No scheme: treat as a filesystem directory so a plain path also overrides.
        return fileOverride(builtin, builtinPrefix, normalizeFile(value), suffix);
    }

    private static TemplateRenderer fileOverride(TemplateEngine builtin, String builtinPrefix, String prefix,
            String suffix) {
        TemplateEngine override = engineWith(fileResolver(prefix, suffix));
        Path dir = Path.of(prefix);
        return new TemplateRenderer(builtin, builtinPrefix, suffix, override,
                name -> Files.exists(dir.resolve(name + suffix)));
    }

    private static TemplateRenderer classpathOverride(TemplateEngine builtin, String builtinPrefix, String prefix,
            String suffix) {
        TemplateEngine override = engineWith(classpathResolver(prefix, suffix));
        ClassLoader classLoader = GeneratorTemplates.class.getClassLoader();
        return new TemplateRenderer(builtin, builtinPrefix, suffix, override,
                name -> classLoader.getResource(prefix + name + suffix) != null);
    }

    private static TemplateEngine engineWith(ITemplateResolver resolver) {
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolvers(Set.of(resolver));
        return engine;
    }

    private static ClassLoaderTemplateResolver classpathResolver(String prefix, String suffix) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(prefix);
        resolver.setSuffix(suffix);
        resolver.setForceSuffix(true);
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        return resolver;
    }

    private static FileTemplateResolver fileResolver(String prefix, String suffix) {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix(prefix);
        resolver.setSuffix(suffix);
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
