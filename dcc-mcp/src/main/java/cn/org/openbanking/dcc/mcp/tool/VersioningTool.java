package cn.org.openbanking.dcc.mcp.tool;

import java.util.List;

import cn.org.openbanking.dcc.application.versioning.VersioningService;
import cn.org.openbanking.dcc.application.versioning.VersioningService.BumpSuggestion;
import cn.org.openbanking.dcc.core.migration.ArtifactType;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.security.TenantContext;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for the versioning model: {@code dcc-hash} (identity), {@code dcc-diff}
 * (change list) and {@code dcc-bump} (advisory SemVer level). An AI assistant can run
 * these during review to flag a breaking upgrade before it ships.
 */
@Component
public class VersioningTool {

    private final VersioningService versioning;

    public VersioningTool(VersioningService versioning) {
        this.versioning = versioning;
    }

    @McpTool(name = "dcc-hash", description = "Return the content hash (model@sha256:...) of an artifact version.")
    public String dccHash(
            @McpToolParam(description = "Artifact type: DATA_STANDARD/TABLE_STRUCTURE/INTERFACE/INTERFACE_TEMPLATE") ArtifactType type,
            @McpToolParam(description = "Artifact id") Long id,
            @McpToolParam(description = "Version, e.g. 1.0.0") String version) {
        return versioning.irHash(TenantContext.get(), type, id, version);
    }

    @McpTool(name = "dcc-diff", description = "Return the change list between two versions of an artifact.")
    public List<FieldChange> dccDiff(
            @McpToolParam(description = "Artifact type") ArtifactType type,
            @McpToolParam(description = "Artifact id") Long id,
            @McpToolParam(description = "From version") String from,
            @McpToolParam(description = "To version") String to) {
        return versioning.diff(TenantContext.get(), type, id, from, to);
    }

    @McpTool(name = "dcc-bump", description = "Suggest an advisory SemVer bump level (MAJOR/MINOR/PATCH/NONE) for a change.")
    public BumpSuggestion dccBump(
            @McpToolParam(description = "Artifact type") ArtifactType type,
            @McpToolParam(description = "Artifact id") Long id,
            @McpToolParam(description = "From version") String from,
            @McpToolParam(description = "To version") String to) {
        return versioning.suggest(TenantContext.get(), type, id, from, to);
    }
}
