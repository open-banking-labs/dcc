package cn.org.openbanking.dcc.application.cli;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import cn.org.openbanking.dcc.application.common.TenantScope;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.table.TableStructureService;
import cn.org.openbanking.dcc.application.template.TemplateService;
import cn.org.openbanking.dcc.application.versioning.VersioningService;
import cn.org.openbanking.dcc.application.versioning.VersioningService.BumpSuggestion;
import cn.org.openbanking.dcc.application.versioning.VersioningService.DriftFinding;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;
import cn.org.openbanking.dcc.core.migration.ArtifactType;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.core.table.content.TableContent;
import cn.org.openbanking.dcc.generator.ddl.TableDdl;
import cn.org.openbanking.dcc.generator.ddl.TableDdlGenerator;
import cn.org.openbanking.dcc.generator.dto.DtoGenerator;
import cn.org.openbanking.dcc.generator.source.GeneratedSource;
import cn.org.openbanking.dcc.generator.source.JavaTypes;
import cn.org.openbanking.dcc.generator.validation.ValidationCodeGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The command-shaped use cases behind {@code /cli/v1/*} (consumed by dcc-cli and
 * dcc-mcp). It is a thin composition over the versioning surface and the generators;
 * the business logic remains in {@code dcc-core} / {@code dcc-generator}.
 */
@Service
@Transactional(readOnly = true)
public class CliService {

    private static final String VALIDATION_PACKAGE = "cn.org.openbanking.dcc.generated.validation";
    private static final String DTO_PACKAGE = "cn.org.openbanking.dcc.generated.dto";
    private static final DateTimeFormatter FLYWAY_VERSION =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.systemDefault());

    private final VersioningService versioning;
    private final DataStandardService standardService;
    private final TableStructureService tableService;
    private final InterfaceService interfaceService;
    private final TemplateService templateService;
    private final ValidationCodeGenerator validationGenerator;
    private final DtoGenerator dtoGenerator;
    private final TableDdlGenerator tableDdlGenerator;
    private final TenantScope tenantScope;

    public CliService(VersioningService versioning,
            DataStandardService standardService,
            TableStructureService tableService,
            InterfaceService interfaceService,
            TemplateService templateService,
            ValidationCodeGenerator validationGenerator,
            DtoGenerator dtoGenerator,
            TableDdlGenerator tableDdlGenerator,
            TenantScope tenantScope) {
        this.versioning = versioning;
        this.standardService = standardService;
        this.tableService = tableService;
        this.interfaceService = interfaceService;
        this.templateService = templateService;
        this.validationGenerator = validationGenerator;
        this.dtoGenerator = dtoGenerator;
        this.tableDdlGenerator = tableDdlGenerator;
        this.tenantScope = tenantScope;
    }

    /** A generated file (relative path + content) for the {@code export} commands. */
    public record ExportFile(String path, String content) {
    }

    /** The outcome of {@code validate}: whether the stored snapshot still verifies. */
    public record ValidationResult(boolean valid, List<String> errors) {
        public ValidationResult {
            errors = List.copyOf(errors);
        }
    }

    public String hash(String tenantId, ArtifactType type, Long id, String version) {
        return versioning.irHash(tenantId, type, id, version);
    }

    public List<FieldChange> diff(String tenantId, ArtifactType type, Long id, String from, String to) {
        return versioning.diff(tenantId, type, id, from, to);
    }

    public BumpSuggestion bump(String tenantId, ArtifactType type, Long id, String from, String to) {
        return versioning.suggest(tenantId, type, id, from, to);
    }

    public ValidationResult validate(String tenantId, ArtifactType type, Long id, String version) {
        boolean intact = tenantScope.call(tenantId, () -> verifyVersion(tenantId, type, id, version));
        return intact
                ? new ValidationResult(true, List.of())
                : new ValidationResult(false,
                        List.of("content hash drift: stored snapshot no longer matches its recorded hash"));
    }

    public List<ExportFile> export(String tenantId, ArtifactType type, Long id, String version, String format) {
        return switch (format == null ? "" : format.toLowerCase()) {
            case "java" -> exportJava(tenantId, type, id, version);
            case "sql" -> exportSql(tenantId, type, id, version);
            default -> throw new ValidationException("unsupported export format: " + format);
        };
    }

    public List<DriftFinding> drift(String tenantId, Long environmentId, Long applicationId) {
        return versioning.drift(tenantId, environmentId, applicationId);
    }

    // ------------------------------------------------------------------ helpers

    private boolean verifyVersion(String tenantId, ArtifactType type, Long id, String version) {
        return switch (type) {
            case DATA_STANDARD -> standardService.verify(tenantId, id, version);
            case TABLE_STRUCTURE -> tableService.verify(tenantId, id, version);
            case INTERFACE -> interfaceService.verify(tenantId, id, version);
            case INTERFACE_TEMPLATE -> templateService.verify(tenantId, id, version);
        };
    }

    private List<ExportFile> exportJava(String tenantId, ArtifactType type, Long id, String version) {
        return tenantScope.call(tenantId, () -> switch (type) {
            case DATA_STANDARD -> {
                var standard = standardService.get(tenantId, id);
                StandardContent content = standardContentAt(tenantId, id, version);
                GeneratedSource source = validationGenerator.generate(VALIDATION_PACKAGE,
                        JavaTypes.capitalize(standard.code()) + "Validation", standard.code(), content);
                yield List.of(new ExportFile(source.fileName(), source.content()));
            }
            case INTERFACE -> {
                var definition = interfaceService.get(tenantId, id);
                InterfaceContent content = interfaceContentAt(tenantId, id, version);
                yield dtoGenerator.generate(DTO_PACKAGE, definition.interfaceNo(), content).stream()
                        .map(source -> new ExportFile(source.fileName(), source.content())).toList();
            }
            default -> List.<ExportFile>of();
        });
    }

    private List<ExportFile> exportSql(String tenantId, ArtifactType type, Long id, String version) {
        return tenantScope.call(tenantId, () -> switch (type) {
            case TABLE_STRUCTURE -> {
                var table = tableService.get(tenantId, id);
                TableStructureService.VersionView snapshot = tableVersionAt(tenantId, id, version);
                TableDdl ddl = tableDdlGenerator.generateCreate(FLYWAY_VERSION.format(Instant.now()), null,
                        table.code(), snapshot.content(), snapshot.contentHash());
                yield List.of(new ExportFile(ddl.flywayFileName(), ddl.flywayScript()));
            }
            default -> List.<ExportFile>of();
        });
    }

    private StandardContent standardContentAt(String tenantId, Long id, String version) {
        return standardService.history(tenantId, id).stream().filter(v -> v.version().equals(version)).findFirst()
                .map(DataStandardService.VersionView::content)
                .orElseThrow(() -> ResourceNotFoundException.of("DataStandardVersion", "version", version));
    }

    private InterfaceContent interfaceContentAt(String tenantId, Long id, String version) {
        return interfaceService.history(tenantId, id).stream().filter(v -> v.version().equals(version)).findFirst()
                .map(InterfaceService.VersionView::content)
                .orElseThrow(() -> ResourceNotFoundException.of("InterfaceVersion", "version", version));
    }

    private TableStructureService.VersionView tableVersionAt(String tenantId, Long id, String version) {
        return tableService.history(tenantId, id).stream().filter(v -> v.version().equals(version)).findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("TableStructureVersion", "version", version));
    }
}
