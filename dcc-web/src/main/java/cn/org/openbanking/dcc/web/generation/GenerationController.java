package cn.org.openbanking.dcc.web.generation;

import java.util.List;

import cn.org.openbanking.dcc.application.generation.GenerationService;
import cn.org.openbanking.dcc.generator.bundle.GeneratedBundle;
import cn.org.openbanking.dcc.generator.source.GeneratedSource;
import cn.org.openbanking.dcc.web.common.AbstractTenantController;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for artifact generation. All operations run inside the caller's
 * tenant.
 */
@RestController
@RequestMapping("/api/generation")
public class GenerationController extends AbstractTenantController {

    private final GenerationService service;

    public GenerationController(GenerationService service) {
        this.service = service;
    }

    @GetMapping("/standards/{id}/validation")
    public GeneratedSource validation(@PathVariable Long id) {
        return service.generateValidation(currentTenantId(), id);
    }

    @GetMapping("/interfaces/{id}/dto")
    public List<GeneratedSource> dto(@PathVariable Long id) {
        return service.generateDto(currentTenantId(), id);
    }

    @GetMapping(value = "/openapi", produces = MediaType.APPLICATION_JSON_VALUE)
    public String openApi(@RequestParam Long environmentId, @RequestParam Long applicationId) {
        return service.generateOpenApi(currentTenantId(), environmentId, applicationId);
    }

    @GetMapping("/jar")
    public ResponseEntity<byte[]> jar(@RequestParam Long environmentId,
            @RequestParam Long applicationId,
            @RequestParam(required = false) String version) {
        GeneratedBundle bundle = service.exportJar(currentTenantId(), environmentId, applicationId, version);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + bundle.fileName() + "\"")
                .contentType(MediaType.parseMediaType("application/java-archive"))
                .body(bundle.content());
    }
}
