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
 * {@code dcc-application}; the caller's tenant comes from the authenticated context and the
 * generation permission is enforced explicitly (ADR 0005).
 */
@Component
public class GenerationTool {

    private final GenerationService generation;
    private final McpAuthorizer authorizer;

    public GenerationTool(GenerationService generation, McpAuthorizer authorizer) {
        this.generation = generation;
        this.authorizer = authorizer;
    }

    @McpTool(name = "generate-validation",
            description = "Generate Java validation code for a data standard (required/length/regex).")
    public GeneratedSource generateValidation(@McpToolParam(description = "Data standard id") Long standardId) {
        authorizer.requireGenerationRead();
        return generation.generateValidation(TenantContext.get(), standardId);
    }

    @McpTool(name = "generate-dto",
            description = "Generate Java Request/Response DTOs for a business interface.")
    public List<GeneratedSource> generateDto(@McpToolParam(description = "Interface id") Long interfaceId) {
        authorizer.requireGenerationRead();
        return generation.generateDto(TenantContext.get(), interfaceId);
    }

    @McpTool(name = "generate-openapi",
            description = "Generate an OpenAPI document (with mock examples) for an environment/application's interfaces.")
    public String generateOpenApi(
            @McpToolParam(description = "Environment id") Long environmentId,
            @McpToolParam(description = "Application id") Long applicationId) {
        authorizer.requireGenerationRead();
        return generation.generateOpenApi(TenantContext.get(), environmentId, applicationId);
    }
}
