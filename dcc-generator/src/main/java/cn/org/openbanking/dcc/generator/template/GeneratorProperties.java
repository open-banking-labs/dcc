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
 *     validation-package: cn.org.openbanking.dcc.generated.validation
 *     dto-package: cn.org.openbanking.dcc.generated.dto
 *     bundle-group: cn.org.openbanking.dcc.generated
 *     default-version: 1.0.0
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

    /** Package generated validation classes are emitted into. */
    private String validationPackage = "cn.org.openbanking.dcc.generated.validation";

    /** Package generated DTO records are emitted into. */
    private String dtoPackage = "cn.org.openbanking.dcc.generated.dto";

    /** Maven group id for the generated artifact bundle. */
    private String bundleGroup = "cn.org.openbanking.dcc.generated";

    /** Version used when a bundle/OpenAPI version is not supplied. */
    private String defaultVersion = "1.0.0";

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

    public String getValidationPackage() {
        return validationPackage;
    }

    public void setValidationPackage(String validationPackage) {
        this.validationPackage = validationPackage;
    }

    public String getDtoPackage() {
        return dtoPackage;
    }

    public void setDtoPackage(String dtoPackage) {
        this.dtoPackage = dtoPackage;
    }

    public String getBundleGroup() {
        return bundleGroup;
    }

    public void setBundleGroup(String bundleGroup) {
        this.bundleGroup = bundleGroup;
    }

    public String getDefaultVersion() {
        return defaultVersion;
    }

    public void setDefaultVersion(String defaultVersion) {
        this.defaultVersion = defaultVersion;
    }
}
