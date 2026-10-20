package cn.org.openbanking.dcc.web.generation;

import cn.org.openbanking.dcc.application.generation.GenerationService;
import cn.org.openbanking.dcc.application.generation.GenerationService.ApplicationRelease;
import cn.org.openbanking.dcc.application.generation.GenerationService.ReleaseSummary;
import cn.org.openbanking.dcc.web.common.AbstractTenantController;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for application release. Artifacts are never generated/exported one
 * by one: an interface is published as part of its <em>application</em> release, which
 * bundles every interface's DTOs plus the validation classes of the data standards they
 * use into a single JAR. All operations run inside the caller's tenant.
 */
@RestController
@RequestMapping("/api/generation")
public class GenerationController extends AbstractTenantController {

    private final GenerationService service;

    public GenerationController(GenerationService service) {
        this.service = service;
    }

    /** The manifest (发布清单) an application release would bundle, for review before publishing. */
    @GetMapping("/manifest")
    @PreAuthorize("hasAuthority(@perms.generationRead)")
    public ReleaseSummary manifest(@RequestParam Long environmentId, @RequestParam Long applicationId) {
        return service.summarizeRelease(currentTenantId(), environmentId, applicationId);
    }

    /** Generates the application's OpenAPI document (环境 + 应用 维度). */
    @GetMapping(value = "/openapi", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority(@perms.generationRead)")
    public String openApi(@RequestParam Long environmentId, @RequestParam Long applicationId) {
        return service.generateOpenApi(currentTenantId(), environmentId, applicationId);
    }

    /** Publishes the application: returns the released JAR bundle (interfaces + validations + OpenAPI). */
    @GetMapping("/release")
    @PreAuthorize("hasAuthority(@perms.generationPackage)")
    public ResponseEntity<byte[]> release(@RequestParam Long environmentId,
            @RequestParam Long applicationId,
            @RequestParam(required = false) String version) {
        ApplicationRelease release = service.releaseApplication(currentTenantId(), environmentId, applicationId,
                version);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + release.bundle().fileName() + "\"")
                .contentType(MediaType.parseMediaType("application/java-archive"))
                .body(release.bundle().content());
    }
}
