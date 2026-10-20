package cn.org.openbanking.dcc.application.standard.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.standard.DataStandardService.ImportResult;
import cn.org.openbanking.dcc.application.standard.DataStandardService.RowError;
import cn.org.openbanking.dcc.application.standard.StandardExportItem;
import cn.org.openbanking.dcc.application.standard.export.ExportProperties;
import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.StandardStatus;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Excel reading/writing for data standards.
 *
 * <p>The same column names drive the import template, the import parser and the
 * generated export, so an exported file can be edited by hand and imported again:
 * the parser ignores the extra {@code 状态/当前版本/更新时间} columns. Import is
 * validated in full before the first write; a single bad row means nothing is applied.
 */
@Service
public class StandardExcelService {

    static final String[] IMPORT_HEADERS = {
            "编码*", "分类", "名称*", "数据类型*", "长度", "精度",
            "必填(是/否)", "默认值", "枚举值", "正则", "示例值", "描述"
    };

    static final String[] EXPORT_HEADERS = {
            "编码", "分类", "名称", "数据类型", "长度", "精度",
            "必填(是/否)", "默认值", "枚举值", "正则", "示例值", "描述",
            "状态", "当前版本", "更新时间"
    };

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final List<String> REQUIRED_HEADERS = List.of("编码", "名称", "数据类型");

    private final DataStandardService standards;
    private final ExportProperties properties;

    public StandardExcelService(DataStandardService standards, ExportProperties properties) {
        this.standards = standards;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ template

    /** Builds the blank import template: a header-only data sheet plus instructions. */
    public byte[] importTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet data = workbook.createSheet("数标");
            Row header = data.createRow(0);
            CellStyle headerStyle = headerStyle(workbook);
            for (int i = 0; i < IMPORT_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(IMPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                data.setColumnWidth(i, 18 * 256);
            }

            Sheet help = workbook.createSheet("填写说明");
            String[] notes = {
                    "按编码匹配：当前环境/应用下已存在则更新，不存在则新建。",
                    "编码、名称、数据类型为必填；长度、精度为非负整数，可留空。",
                    "必填列填写“是”或“否”，留空视为“否”。",
                    "单次最多导入 " + properties.getImportMaxRows() + " 行，文件不超过 10MB。",
                    "不要修改表头；状态、当前版本、更新时间等额外列会被忽略。"
            };
            for (int i = 0; i < notes.length; i++) {
                help.createRow(i).createCell(0).setCellValue(notes[i]);
            }
            help.setColumnWidth(0, 80 * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("cannot build standards import template", ex);
        }
    }

    // ------------------------------------------------------------------ import

    /**
     * Parses and applies one spreadsheet. Every row is validated before anything is
     * written; when {@code errors} is non-empty the caller must treat the import as
     * rejected and nothing has been persisted.
     */
    public ImportResult importXlsx(String tenantId, Long environmentId, Long applicationId,
            String fileName, InputStream input) {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new ValidationException("仅支持 .xlsx 文件");
        }
        List<StandardExportItem> items = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ValidationException("文件中没有工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.ROOT);

            Row header = sheet.getRow(sheet.getFirstRowNum());
            Map<Column, Integer> columns = mapColumns(header, formatter, evaluator);
            for (String required : REQUIRED_HEADERS) {
                Column column = Column.ofHeader(required);
                if (column != null && !columns.containsKey(column)) {
                    throw new ValidationException("缺少必填列：" + required);
                }
            }

            int dataRows = 0;
            for (int rowIndex = header.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlank(row, formatter, evaluator)) {
                    continue;
                }
                dataRows++;
                if (dataRows > properties.getImportMaxRows()) {
                    throw new ValidationException("单次最多导入 " + properties.getImportMaxRows() + " 行");
                }
                ParsedRow parsed = parseRow(rowIndex + 1, row, columns, formatter, evaluator, errors);
                if (parsed == null) {
                    continue;
                }
                if (!codes.add(parsed.code())) {
                    errors.add(new RowError(rowIndex + 1, "文件内编码重复：" + parsed.code()));
                    continue;
                }
                items.add(new StandardExportItem(parsed.code(), parsed.category(), parsed.content()));
            }
            if (dataRows == 0) {
                throw new ValidationException("文件中没有可导入的数据");
            }
        } catch (ValidationException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ValidationException("无法读取 Excel 文件：" + ex.getMessage());
        }

        if (!errors.isEmpty()) {
            return new ImportResult(0, 0, List.copyOf(errors));
        }
        ImportResult applied = standards.importItems(tenantId, environmentId, applicationId, items);
        return new ImportResult(applied.created(), applied.updated(), List.of());
    }

    private ParsedRow parseRow(int excelRow, Row row, Map<Column, Integer> columns,
            DataFormatter formatter, FormulaEvaluator evaluator, List<RowError> errors) {
        List<String> rowErrors = new ArrayList<>();
        String code = value(row, columns.get(Column.CODE), formatter, evaluator);
        String name = value(row, columns.get(Column.NAME), formatter, evaluator);
        String dataType = value(row, columns.get(Column.DATA_TYPE), formatter, evaluator);
        String category = value(row, columns.get(Column.CATEGORY), formatter, evaluator);
        String description = value(row, columns.get(Column.DESCRIPTION), formatter, evaluator);
        String defaultValue = value(row, columns.get(Column.DEFAULT_VALUE), formatter, evaluator);
        String enumValues = value(row, columns.get(Column.ENUM_VALUES), formatter, evaluator);
        String regex = value(row, columns.get(Column.REGEX), formatter, evaluator);
        String exampleValue = value(row, columns.get(Column.EXAMPLE_VALUE), formatter, evaluator);

        if (code == null) {
            rowErrors.add("编码不能为空");
        } else if (code.length() > 128) {
            rowErrors.add("编码长度不能超过 128");
        }
        if (name == null) {
            rowErrors.add("名称不能为空");
        } else if (name.length() > 256) {
            rowErrors.add("名称长度不能超过 256");
        }
        if (dataType == null) {
            rowErrors.add("数据类型不能为空");
        } else if (dataType.length() > 64) {
            rowErrors.add("数据类型长度不能超过 64");
        }
        if (category != null && category.length() > 64) {
            rowErrors.add("分类长度不能超过 64");
        }
        if (description != null && description.length() > 1024) {
            rowErrors.add("描述长度不能超过 1024");
        }
        if (defaultValue != null && defaultValue.length() > 256) {
            rowErrors.add("默认值长度不能超过 256");
        }
        if (enumValues != null && enumValues.length() > 1024) {
            rowErrors.add("枚举值长度不能超过 1024");
        }
        if (regex != null && regex.length() > 512) {
            rowErrors.add("正则长度不能超过 512");
        }
        if (exampleValue != null && exampleValue.length() > 512) {
            rowErrors.add("示例值长度不能超过 512");
        }

        Integer length = integerValue(row, columns.get(Column.LENGTH), formatter, evaluator, "长度", rowErrors);
        Integer scale = integerValue(row, columns.get(Column.SCALE), formatter, evaluator, "精度", rowErrors);
        boolean required = booleanValue(row, columns.get(Column.REQUIRED), formatter, evaluator, rowErrors);

        if (!rowErrors.isEmpty()) {
            errors.add(new RowError(excelRow, String.join("；", rowErrors)));
            return null;
        }
        StandardContent content = new StandardContent(name, description, dataType, length, scale, required,
                defaultValue, enumValues, regex, exampleValue);
        return new ParsedRow(code, category, content);
    }

    private Map<Column, Integer> mapColumns(Row header, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<Column, Integer> columns = new HashMap<>();
        if (header == null) {
            throw new ValidationException("缺少表头");
        }
        for (Cell cell : header) {
            String raw = formatter.formatCellValue(cell, evaluator);
            Column column = Column.ofHeader(raw);
            if (column != null) {
                columns.putIfAbsent(column, cell.getColumnIndex());
            }
        }
        return columns;
    }

    private static boolean isBlank(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell, evaluator).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String value(Row row, Integer index, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (index == null) {
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell, evaluator).trim();
        return value.isEmpty() ? null : value;
    }

    private static Integer integerValue(Row row, Integer index, DataFormatter formatter,
            FormulaEvaluator evaluator, String label, List<String> errors) {
        String value = value(row, index, formatter, evaluator);
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                errors.add(label + "不能为负数");
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            errors.add(label + "必须是整数");
            return null;
        }
    }

    private static boolean booleanValue(Row row, Integer index, DataFormatter formatter,
            FormulaEvaluator evaluator, List<String> errors) {
        String value = value(row, index, formatter, evaluator);
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("是") || normalized.equals("true") || normalized.equals("1")
                || normalized.equals("y") || normalized.equals("yes")) {
            return true;
        }
        if (normalized.equals("否") || normalized.equals("false") || normalized.equals("0")
                || normalized.equals("n") || normalized.equals("no")) {
            return false;
        }
        errors.add("必填列只能填写“是”或“否”");
        return false;
    }

    // ------------------------------------------------------------------ export

    /**
     * Streams rows into an .xlsx file and returns how many data rows were written.
     * SXSSF keeps only a small window in memory and spills the rest to temp files.
     */
    public int write(Path file, Iterator<StandardExportRow> rows) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(500)) {
            Sheet sheet = workbook.createSheet("数标");
            CellStyle headerStyle = headerStyle(workbook);
            Row header = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(EXPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 18 * 256);
            }

            int rowIndex = 1;
            while (rows.hasNext()) {
                StandardExportRow source = rows.next();
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(source.code());
                set(row, 1, source.category());
                set(row, 2, source.name());
                set(row, 3, source.dataType());
                setNumber(row, 4, source.length());
                setNumber(row, 5, source.scale());
                row.createCell(6).setCellValue(source.required() ? "是" : "否");
                set(row, 7, source.defaultValue());
                set(row, 8, source.enumValues());
                set(row, 9, source.regex());
                set(row, 10, source.exampleValue());
                set(row, 11, source.description());
                set(row, 12, source.status() == null ? null : source.status().name());
                set(row, 13, source.version());
                set(row, 14, source.updatedAt() == null ? null : DATE_TIME.format(source.updatedAt()));
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
            workbook.dispose();
            return rowIndex - 1;
        }
    }

    private static void set(Row row, int index, String value) {
        if (value != null) {
            row.createCell(index).setCellValue(value);
        }
    }

    private static void setNumber(Row row, int index, Integer value) {
        if (value != null) {
            row.createCell(index).setCellValue(value);
        }
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    // ------------------------------------------------------------------ records

    /** One data-standard row in an export workbook. */
    public record StandardExportRow(String code, String category, String name, String dataType,
            Integer length, Integer scale, boolean required, String defaultValue, String enumValues,
            String regex, String exampleValue, String description, StandardStatus status,
            String version, Instant updatedAt) {
    }

    private record ParsedRow(String code, String category, StandardContent content) {
    }

    /** Stable, header-name-based column mapping; aliases tolerate the export file. */
    private enum Column {
        CODE,
        CATEGORY,
        NAME,
        DATA_TYPE,
        LENGTH,
        SCALE,
        REQUIRED,
        DEFAULT_VALUE,
        ENUM_VALUES,
        REGEX,
        EXAMPLE_VALUE,
        DESCRIPTION;

        static Column ofHeader(String raw) {
            if (raw == null) {
                return null;
            }
            String header = raw.replace("*", "").trim();
            if (header.startsWith("必填")) {
                return REQUIRED;
            }
            return switch (header) {
                case "编码" -> CODE;
                case "分类" -> CATEGORY;
                case "名称" -> NAME;
                case "数据类型" -> DATA_TYPE;
                case "长度" -> LENGTH;
                case "精度" -> SCALE;
                case "默认值" -> DEFAULT_VALUE;
                case "枚举值" -> ENUM_VALUES;
                case "正则" -> REGEX;
                case "示例值" -> EXAMPLE_VALUE;
                case "描述" -> DESCRIPTION;
                default -> null;
            };
        }
    }
}
