package cn.org.openbanking.dcc.generator.template;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the template-driven generators, bound from
 * {@code dcc.generator.*}. Every property has a default that reproduces the
 * pre-templating behaviour, so an application that sets nothing is unaffected.
 *
 * <pre>
 * dcc:
 *   generator:
 *     template-dir: classpath:/templates/   # override with file:/path/to/custom/
 *     package-prefix: ""
 *     entity-suffix: ""
 *     repository-suffix: Repository
 *     dto-suffix: Dto
 * </pre>
 */
@ConfigurationProperties(prefix = "dcc.generator")
public class GeneratorProperties {

    /**
     * Root the template files are resolved from. Accepts a Spring-style resource
     * location ({@code classpath:/templates/}) or a filesystem location
     * ({@code file:/path/to/custom/} or a plain path). Custom files take precedence
     * over the built-in defaults on the classpath.
     */
    private String templateDir = "classpath:/templates/";

    /** Optional package prefix prepended to generated packages. Empty by default. */
    private String packagePrefix = "";

    /** Suffix for generated JPA entity classes. Empty by default. */
    private String entitySuffix = "";

    /** Suffix for generated repository interfaces. */
    private String repositorySuffix = "Repository";

    /** Suffix for generated (nested) DTO records. */
    private String dtoSuffix = "Dto";

    public String getTemplateDir() {
        return templateDir;
    }

    public void setTemplateDir(String templateDir) {
        this.templateDir = templateDir;
    }

    public String getPackagePrefix() {
        return packagePrefix;
    }

    public void setPackagePrefix(String packagePrefix) {
        this.packagePrefix = packagePrefix;
    }

    public String getEntitySuffix() {
        return entitySuffix;
    }

    public void setEntitySuffix(String entitySuffix) {
        this.entitySuffix = entitySuffix;
    }

    public String getRepositorySuffix() {
        return repositorySuffix;
    }

    public void setRepositorySuffix(String repositorySuffix) {
        this.repositorySuffix = repositorySuffix;
    }

    public String getDtoSuffix() {
        return dtoSuffix;
    }

    public void setDtoSuffix(String dtoSuffix) {
        this.dtoSuffix = dtoSuffix;
    }
}
