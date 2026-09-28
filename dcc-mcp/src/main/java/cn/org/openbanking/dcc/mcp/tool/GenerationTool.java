package cn.org.openbanking.dcc.mcp.tool;

import java.util.List;

import cn.org.openbanking.dcc.application.generation.GenerationService;
import cn.org.openbanking.dcc.generator.source.GeneratedSource;
import cn.org.openbanking.dcc.security.TenantContext;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for generating downstream artifacts. Thin adapters over
 * {@code dcc-application}; the caller's tenant comes from the authenticated context.
 */
@Component
public class GenerationTool {

    private final GenerationService generation;

    public GenerationTool(GenerationService generation) {
        this.generation = generation;
    }

    @McpTool(name = "generate-validation",
            description = "Generate Java validation code for a data standard (required/length/regex).")
    public GeneratedSource generateValidation(@McpToolParam(description = "Data standard id") Long standardId) {
        return generation.generateValidation(TenantContext.get(), standardId);
    }

    @McpTool(name = "generate-dto",
            description = "Generate Java Request/Response DTOs for a business interface.")
    public List<GeneratedSource> generateDto(@McpToolParam(description = "Interface id") Long interfaceId) {
        return generation.generateDto(TenantContext.get(), interfaceId);
    }

    @McpTool(name = "generate-openapi",
            description = "Generate an OpenAPI document (with mock examples) for an environment/application's interfaces.")
    public String generateOpenApi(
            @McpToolParam(description = "Environment id") Long environmentId,
            @McpToolParam(description = "Application id") Long applicationId) {
        return generation.generateOpenApi(TenantContext.get(), environmentId, applicationId);
    }
}
