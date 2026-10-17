package cn.org.openbanking.dcc.mcp.tool;

import java.util.List;

import cn.org.openbanking.dcc.application.audit.AuditService;
import cn.org.openbanking.dcc.application.audit.AuditService.AuditView;
import cn.org.openbanking.dcc.application.audit.AuditService.VerifyView;
import cn.org.openbanking.dcc.core.common.audit.AuditAction;
import cn.org.openbanking.dcc.security.TenantContext;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Read-only MCP access to the caller tenant's audit trail, so an AI assistant can inspect
 * who changed a row and (re-)verify its hash chain during review. Restores are deliberately
 * not exposed here: writes and privileged operations deliberately flow through REST / the
 * command API, which enforce the admin authority.
 */
@Component
public class AuditTool {

    private static final int PAGE_SIZE = 50;

    private final AuditService auditService;

    public AuditTool(AuditService auditService) {
        this.auditService = auditService;
    }

    @McpTool(name = "dcc-audit-history", description = "Return the full, oldest-first audit history of one row (who, when, why and the before/after images).")
    public List<AuditView> history(
            @McpToolParam(description = "Entity type, e.g. DataStandard/TableStructure/InterfaceDefinition/InterfaceTemplate") String entityType,
            @McpToolParam(description = "Entity id") Long entityId) {
        return auditService.history(TenantContext.get(), entityType, entityId);
    }

    @McpTool(name = "dcc-audit-search",
            description = "Search the audit trail of the caller's tenant, newest first; every filter is optional.")
    public List<AuditView> search(
            @McpToolParam(required = false, description = "Entity type filter") String entityType,
            @McpToolParam(required = false, description = "Entity id filter") Long entityId,
            @McpToolParam(required = false, description = "Actor filter") String actor,
            @McpToolParam(required = false, description = "Action filter: CREATE/UPDATE/LOGICAL_DELETE/RESTORE") AuditAction action,
            @McpToolParam(required = false, description = "Page number (50 per page)") Integer page) {
        Page<AuditView> result = auditService.search(TenantContext.get(), entityType, entityId, actor, action,
                null, null, PageRequest.of(page == null ? 0 : page, PAGE_SIZE));
        return result.getContent();
    }

    @McpTool(name = "dcc-audit-verify",
            description = "Recompute the tamper-evident hash chain of one row's audit history and report where it breaks, if anywhere.")
    public VerifyView verify(
            @McpToolParam(description = "Entity type") String entityType,
            @McpToolParam(description = "Entity id") Long entityId) {
        return auditService.verify(TenantContext.get(), entityType, entityId);
    }
}
