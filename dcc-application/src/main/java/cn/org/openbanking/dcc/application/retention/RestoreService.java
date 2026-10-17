package cn.org.openbanking.dcc.application.retention;

import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.table.TableStructureService;
import cn.org.openbanking.dcc.application.template.TemplateService;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.migration.ArtifactType;

import org.springframework.stereotype.Service;

/**
 * Use case: bring a logically-deleted aggregate back into the default view. The target
 * service performs the tenant scoping, the audit trail records a {@code RESTORE} entry and
 * the partial unique index arbitrates any code-reuse conflict.
 *
 * <p>Exposed through the REST admin endpoints and the command API with an administrator
 * authority; the MCP surface stays read-only.
 */
@Service
public class RestoreService {

    private final DataStandardService standards;
    private final TableStructureService tables;
    private final InterfaceService interfaces;
    private final TemplateService templates;

    public RestoreService(DataStandardService standards, TableStructureService tables,
            InterfaceService interfaces, TemplateService templates) {
        this.standards = standards;
        this.tables = tables;
        this.interfaces = interfaces;
        this.templates = templates;
    }

    /**
     * @throws ResourceNotFoundException when the row does not exist at all
     * @throws ConflictException         when a live row already reused the deleted row's code
     *                                   (the partial unique index is the arbiter)
     * @return the concrete {@code *View} of the restored aggregate, determined by {@code type}
     */
    public Object restore(String tenantId, ArtifactType type, Long id) {
        return switch (type) {
            case DATA_STANDARD -> standards.restore(tenantId, id);
            case TABLE_STRUCTURE -> tables.restore(tenantId, id);
            case INTERFACE -> interfaces.restore(tenantId, id);
            case INTERFACE_TEMPLATE -> templates.restore(tenantId, id);
        };
    }
}
