package cn.org.openbanking.dcc.web.cli;

import java.util.List;

import cn.org.openbanking.dcc.application.cli.CliService;
import cn.org.openbanking.dcc.application.cli.CliService.ExportFile;
import cn.org.openbanking.dcc.application.cli.CliService.ValidationResult;
import cn.org.openbanking.dcc.application.versioning.VersioningService.BumpSuggestion;
import cn.org.openbanking.dcc.application.versioning.VersioningService.DriftFinding;
import cn.org.openbanking.dcc.core.migration.ArtifactType;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.web.common.AbstractTenantController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The {@code /cli/<version>/*} API group: slim, command-shaped endpoints consumed by
 * {@code dcc-cli} and {@code dcc-mcp}. Distinct from the richer {@code /api/*} group
 * used by the web UI; both share the same {@code dcc-application} / {@code dcc-core}.
 * The version segment is the {@code dcc.api.version} property (default {@code v1}).
 */
@RestController
@RequestMapping("/cli/${dcc.api.version:v1}")
public class CliController extends AbstractTenantController {

    private final CliService service;

    public CliController(CliService service) {
        this.service = service;
    }

    public record ModelRequest(@NotNull ArtifactType type, @NotNull Long modelId, @NotNull String version) {
    }

    public record RangeRequest(@NotNull ArtifactType type, @NotNull Long modelId,
            @NotNull String fromVersion, @NotNull String toVersion) {
    }

    public record DriftRequest(@NotNull Long environmentId, @NotNull Long applicationId) {
    }

    public record HashResponse(String hash) {
    }

    public record DiffResponse(List<FieldChange> changes) {
    }

    public record ExportResponse(List<ExportFile> files) {
    }

    public record DriftResponse(List<DriftFinding> drifted) {
    }

    @PostMapping("/hash")
    public HashResponse hash(@Valid @RequestBody ModelRequest request) {
        return new HashResponse(service.hash(currentTenantId(), request.type(), request.modelId(), request.version()));
    }

    @PostMapping("/diff")
    public DiffResponse diff(@Valid @RequestBody RangeRequest request) {
        return new DiffResponse(service.diff(currentTenantId(), request.type(), request.modelId(),
                request.fromVersion(), request.toVersion()));
    }

    @PostMapping("/bump")
    public BumpSuggestion bump(@Valid @RequestBody RangeRequest request) {
        return service.bump(currentTenantId(), request.type(), request.modelId(),
                request.fromVersion(), request.toVersion());
    }

    @PostMapping("/validate")
    public ValidationResult validate(@Valid @RequestBody ModelRequest request) {
        return service.validate(currentTenantId(), request.type(), request.modelId(), request.version());
    }

    @PostMapping("/export/java")
    public ExportResponse exportJava(@Valid @RequestBody ModelRequest request) {
        return new ExportResponse(service.export(currentTenantId(), request.type(), request.modelId(),
                request.version(), "java"));
    }

    @PostMapping("/export/sql")
    public ExportResponse exportSql(@Valid @RequestBody ModelRequest request) {
        return new ExportResponse(service.export(currentTenantId(), request.type(), request.modelId(),
                request.version(), "sql"));
    }

    @PostMapping("/drift")
    public DriftResponse drift(@Valid @RequestBody DriftRequest request) {
        return new DriftResponse(service.drift(currentTenantId(), request.environmentId(), request.applicationId()));
    }
}
