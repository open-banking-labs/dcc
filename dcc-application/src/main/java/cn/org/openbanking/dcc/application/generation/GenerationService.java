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
import cn.org.openbanking.dcc.generator.template.GeneratorProperties;
import cn.org.openbanking.dcc.generator.validation.ValidationCodeGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates artifact generation (代码/产物生成): validation code from data
 * standards, DTO / Request / Response from interfaces, an OpenAPI document, and a
 * per-tenant-per-environment JAR bundle with Maven coordinates and a version.
 *
 * <p>The generators live in {@code dcc-generator} and are language-extensible; this
 * service wires them to the tenant-scoped models. The output packages, bundle group
 * and default version are supplied by {@link GeneratorProperties}
 * ({@code dcc.generator.*}).
 */
@Service
@Transactional(readOnly = true)
public class GenerationService {

    private final DataStandardService standardService;
    private final InterfaceService interfaceService;
    private final ValidationCodeGenerator validationGenerator;
    private final DtoGenerator dtoGenerator;
    private final OpenApiGenerator openApiGenerator;
    private final JarBundleGenerator jarBundleGenerator;
    private final GeneratorProperties generatorProperties;
    private final TenantScope tenantScope;

    public GenerationService(DataStandardService standardService,
            InterfaceService interfaceService,
            ValidationCodeGenerator validationGenerator,
            DtoGenerator dtoGenerator,
            OpenApiGenerator openApiGenerator,
            JarBundleGenerator jarBundleGenerator,
            GeneratorProperties generatorProperties,
            TenantScope tenantScope) {
        this.standardService = standardService;
        this.interfaceService = interfaceService;
        this.validationGenerator = validationGenerator;
        this.dtoGenerator = dtoGenerator;
        this.openApiGenerator = openApiGenerator;
        this.jarBundleGenerator = jarBundleGenerator;
        this.generatorProperties = generatorProperties;
        this.tenantScope = tenantScope;
    }

    /** Interfaces + data standards that a release of the scope would bundle (发布清单). */
    public record ReleaseSummary(Long environmentId, Long applicationId, List<String> interfaceNos,
            List<String> standardCodes) {
    }

    /** The downloadable bundle produced by an application release, with its manifest. */
    public record ApplicationRelease(GeneratedBundle bundle, List<String> interfaceNos, List<String> standardCodes,
            String version) {
    }

    public GeneratedSource generateValidation(String tenantId, Long standardId) {
        return tenantScope.call(tenantId, () -> {
            var standard = standardService.get(tenantId, standardId);
            return validationGenerator.generate(generatorProperties.getValidationPackage(),
                    JavaTypes.capitalize(standard.code()) + "Validation", standard.code(), standard.content());
        });
    }

    public List<GeneratedSource> generateDto(String tenantId, Long interfaceId) {
        return tenantScope.call(tenantId, () -> {
            var definition = interfaceService.get(tenantId, interfaceId);
            return dtoGenerator.generate(generatorProperties.getDtoPackage(), definition.interfaceNo(),
                    definition.content());
        });
    }

    public String generateOpenApi(String tenantId, Long environmentId, Long applicationId) {
        return tenantScope.call(tenantId,
                () -> openApiGenerator.generate("DCC contracts", generatorProperties.getDefaultVersion(),
                        interfaceSpecs(tenantId, environmentId, applicationId)));
    }

    /**
     * A preview of what an application release would bundle: the interfaces and the
     * data standards the whole environment+application scope contains. This is the
     * "发布清单" surfaced before an actual release, so publishing is a deliberate,
     * reviewed, application-level action rather than an ad-hoc per-artifact export.
     */
    public ReleaseSummary summarizeRelease(String tenantId, Long environmentId, Long applicationId) {
        return tenantScope.call(tenantId, () -> {
            List<String> interfaceNos = interfaceService.search(tenantId, environmentId, applicationId, null).stream()
                    .map(InterfaceService.InterfaceView::interfaceNo).toList();
            List<String> standardCodes = standardService.search(tenantId, environmentId, applicationId, null, null)
                    .stream().map(DataStandardService.StandardView::code).toList();
            return new ReleaseSummary(environmentId, applicationId, interfaceNos, standardCodes);
        });
    }

    /**
     * Releases an application: bundles <em>all</em> its interfaces (DTOs) plus the
     * validation classes of the data standards they use into one JAR with a manifest.
     * This is the single "generate/publish" verb for interfaces — there is no
     * per-interface export. One JAR per environment + application, carrying a pom with
     * Maven coordinates.
     */
    public ApplicationRelease releaseApplication(String tenantId, Long environmentId, Long applicationId,
            String version) {
        String effectiveVersion = (version == null || version.isBlank()) ? generatorProperties.getDefaultVersion()
                : version;
        return tenantScope.call(tenantId, () -> {
            Collected collected = collectSources(tenantId, environmentId, applicationId, effectiveVersion);

            String artifactId = "dcc-" + sanitize(tenantId) + "-env" + environmentId + "-app" + applicationId;
            GeneratedBundle bundle = jarBundleGenerator.generate(generatorProperties.getBundleGroup(), artifactId,
                    effectiveVersion, collected.sources());
            return new ApplicationRelease(bundle, collected.interfaceNos(), collected.standardCodes(),
                    effectiveVersion);
        });
    }

    /**
     * @deprecated kept only for internal bundle reuse and compatibility; prefer
     * {@link #releaseApplication}. One JAR per tenant + environment.
     */
    @Deprecated
    public GeneratedBundle exportJar(String tenantId, Long environmentId, Long applicationId, String version) {
        String effectiveVersion = (version == null || version.isBlank()) ? generatorProperties.getDefaultVersion()
                : version;
        return tenantScope.call(tenantId, () -> {
            Collected collected = collectSources(tenantId, environmentId, applicationId, effectiveVersion);
            String artifactId = "dcc-" + sanitize(tenantId) + "-env" + environmentId;
            return jarBundleGenerator.generate(generatorProperties.getBundleGroup(), artifactId, effectiveVersion,
                    collected.sources());
        });
    }

    /** Collects every generated source for the scope, plus the interfaces/standards it covered. */
    private Collected collectSources(String tenantId, Long environmentId, Long applicationId, String version) {
        List<GeneratedSource> sources = new ArrayList<>();
        List<String> standardCodes = new ArrayList<>();
        standardService.search(tenantId, environmentId, applicationId, null, null).forEach(standard -> {
            sources.add(validationGenerator.generate(generatorProperties.getValidationPackage(),
                    JavaTypes.capitalize(standard.code()) + "Validation", standard.code(), standard.content()));
            standardCodes.add(standard.code());
        });
        List<String> interfaceNos = new ArrayList<>();
        interfaceService.search(tenantId, environmentId, applicationId, null).forEach(definition -> {
            sources.addAll(dtoGenerator.generate(generatorProperties.getDtoPackage(), definition.interfaceNo(),
                    definition.content()));
            interfaceNos.add(definition.interfaceNo());
        });
        sources.add(new GeneratedSource("openapi.json", openApiGenerator.generate("DCC contracts", version,
                interfaceSpecs(tenantId, environmentId, applicationId))));
        return new Collected(sources, interfaceNos, standardCodes);
    }

    private record Collected(List<GeneratedSource> sources, List<String> interfaceNos, List<String> standardCodes) {
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
