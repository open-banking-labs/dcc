package cn.org.openbanking.dcc.application.generation;

import java.util.ArrayList;
import java.util.List;

import cn.org.openbanking.dcc.application.common.TenantScope;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.generator.bundle.GeneratedBundle;
import cn.org.openbanking.dcc.generator.bundle.JarBundleGenerator;
import cn.org.openbanking.dcc.generator.dto.DtoGenerator;
import cn.org.openbanking.dcc.generator.openapi.InterfaceSpec;
import cn.org.openbanking.dcc.generator.openapi.OpenApiGenerator;
import cn.org.openbanking.dcc.generator.source.GeneratedSource;
import cn.org.openbanking.dcc.generator.source.JavaTypes;
import cn.org.openbanking.dcc.generator.validation.ValidationCodeGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates artifact generation (代码/产物生成): validation code from data
 * standards, DTO / Request / Response from interfaces, an OpenAPI document, and a
 * per-tenant-per-environment JAR bundle with Maven coordinates and a version.
 *
 * <p>The generators live in {@code dcc-generator} and are language-extensible; this
 * service wires them to the tenant-scoped models.
 */
@Service
@Transactional(readOnly = true)
public class GenerationService {

    private static final String VALIDATION_PACKAGE = "cn.org.openbanking.dcc.generated.validation";
    private static final String DTO_PACKAGE = "cn.org.openbanking.dcc.generated.dto";
    private static final String BUNDLE_GROUP = "cn.org.openbanking.dcc.generated";
    private static final String DEFAULT_VERSION = "1.0.0";

    private final DataStandardService standardService;
    private final InterfaceService interfaceService;
    private final ValidationCodeGenerator validationGenerator;
    private final DtoGenerator dtoGenerator;
    private final OpenApiGenerator openApiGenerator;
    private final JarBundleGenerator jarBundleGenerator;
    private final TenantScope tenantScope;

    public GenerationService(DataStandardService standardService,
            InterfaceService interfaceService,
            ValidationCodeGenerator validationGenerator,
            DtoGenerator dtoGenerator,
            OpenApiGenerator openApiGenerator,
            JarBundleGenerator jarBundleGenerator,
            TenantScope tenantScope) {
        this.standardService = standardService;
        this.interfaceService = interfaceService;
        this.validationGenerator = validationGenerator;
        this.dtoGenerator = dtoGenerator;
        this.openApiGenerator = openApiGenerator;
        this.jarBundleGenerator = jarBundleGenerator;
        this.tenantScope = tenantScope;
    }

    public GeneratedSource generateValidation(String tenantId, Long standardId) {
        return tenantScope.call(tenantId, () -> {
            var standard = standardService.get(tenantId, standardId);
            return validationGenerator.generate(VALIDATION_PACKAGE,
                    JavaTypes.capitalize(standard.code()) + "Validation", standard.code(), standard.content());
        });
    }

    public List<GeneratedSource> generateDto(String tenantId, Long interfaceId) {
        return tenantScope.call(tenantId, () -> {
            var definition = interfaceService.get(tenantId, interfaceId);
            return dtoGenerator.generate(DTO_PACKAGE, definition.interfaceNo(), definition.content());
        });
    }

    public String generateOpenApi(String tenantId, Long environmentId, Long applicationId) {
        return tenantScope.call(tenantId,
                () -> openApiGenerator.generate("DCC contracts", DEFAULT_VERSION,
                        interfaceSpecs(tenantId, environmentId, applicationId)));
    }

    /** One JAR per tenant + environment, carrying a pom with Maven coordinates. */
    public GeneratedBundle exportJar(String tenantId, Long environmentId, Long applicationId, String version) {
        String effectiveVersion = (version == null || version.isBlank()) ? DEFAULT_VERSION : version;
        return tenantScope.call(tenantId, () -> {
            List<GeneratedSource> sources = new ArrayList<>();
            standardService.search(tenantId, environmentId, applicationId, null, null).forEach(standard ->
                    sources.add(validationGenerator.generate(VALIDATION_PACKAGE,
                            JavaTypes.capitalize(standard.code()) + "Validation", standard.code(),
                            standard.content())));
            interfaceService.search(tenantId, environmentId, applicationId, null).forEach(definition ->
                    sources.addAll(dtoGenerator.generate(DTO_PACKAGE, definition.interfaceNo(), definition.content())));
            sources.add(new GeneratedSource("openapi.json",
                    openApiGenerator.generate("DCC contracts", effectiveVersion,
                            interfaceSpecs(tenantId, environmentId, applicationId))));

            String artifactId = "dcc-" + sanitize(tenantId) + "-env" + environmentId;
            return jarBundleGenerator.generate(BUNDLE_GROUP, artifactId, effectiveVersion, sources);
        });
    }

    private List<InterfaceSpec> interfaceSpecs(String tenantId, Long environmentId, Long applicationId) {
        return interfaceService.search(tenantId, environmentId, applicationId, null).stream()
                .map(definition -> new InterfaceSpec(definition.interfaceNo(), definition.content().name(),
                        definition.content().url(), definition.content()))
                .toList();
    }

    private static String sanitize(String tenantId) {
        return tenantId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
