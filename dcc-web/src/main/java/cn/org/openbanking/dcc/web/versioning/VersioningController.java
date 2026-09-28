package cn.org.openbanking.dcc.web.versioning;

import java.util.List;

import cn.org.openbanking.dcc.application.versioning.VersioningService;
import cn.org.openbanking.dcc.application.versioning.VersioningService.BumpSuggestion;
import cn.org.openbanking.dcc.application.versioning.VersioningService.DriftFinding;
import cn.org.openbanking.dcc.core.migration.ArtifactType;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.web.common.AbstractTenantController;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints mirroring the {@code dcc hash / diff / bump} commands, plus a drift
 * check. All operations run inside the caller's tenant.
 */
@RestController
@RequestMapping("/api/versioning")
public class VersioningController extends AbstractTenantController {

    private final VersioningService service;

    public VersioningController(VersioningService service) {
        this.service = service;
    }

    /** {@code dcc hash} for a stored version. */
    @GetMapping("/{type}/{id}/hash")
    public String hash(@PathVariable ArtifactType type, @PathVariable Long id, @RequestParam String version) {
        return service.irHash(currentTenantId(), type, id, version);
    }

    /** {@code dcc hash} for raw content (the request body is the model JSON). */
    @PostMapping("/{type}/hash")
    public String hashContent(@PathVariable ArtifactType type, @RequestBody String json) {
        return service.hashContent(type, json);
    }

    /** {@code dcc diff}: the human-readable change list between two versions. */
    @GetMapping("/{type}/{id}/diff")
    public List<FieldChange> diff(@PathVariable ArtifactType type, @PathVariable Long id,
            @RequestParam String from, @RequestParam String to) {
        return service.diff(currentTenantId(), type, id, from, to);
    }

    /** {@code dcc bump}: the advisory SemVer level for the change between two versions. */
    @GetMapping("/{type}/{id}/bump")
    public BumpSuggestion bump(@PathVariable ArtifactType type, @PathVariable Long id,
            @RequestParam String from, @RequestParam String to) {
        return service.suggest(currentTenantId(), type, id, from, to);
    }

    /** Drift check: artifacts in scope whose stored snapshots no longer verify. */
    @GetMapping("/drift")
    public List<DriftFinding> drift(@RequestParam Long environmentId, @RequestParam Long applicationId) {
        return service.drift(currentTenantId(), environmentId, applicationId);
    }
}
