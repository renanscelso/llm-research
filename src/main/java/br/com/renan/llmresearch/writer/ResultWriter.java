package br.com.renan.llmresearch.writer;

import br.com.renan.llmresearch.config.ResearchProperties;
import br.com.renan.llmresearch.model.ResearchResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Exporta resultados para XLSX, CSV, JSONL e JSON formatado em pasta por execução.
 *
 * @author Renan Celso
 */
@Component
public class ResultWriter implements AutoCloseable {

    public static final int EXCEL_MAX_CELL_LENGTH = 32_767;
    private static final int EXCEL_MAX_ROWS = 1_048_575;

    private static final String[] HEADERS = {
            "ID da execução",
            "ID da pergunta",
            "Pergunta",
            "Provedor",
            "Modelo",
            "Repetição",
            "Resposta",
            "Status",
            "Erro",
            "Data/hora",
            "Latência (ms)",
            "Tokens de entrada",
            "Tokens de saída"
    };

    private static final DateTimeFormatter RUN_FOLDER_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter CELL_TIMESTAMP =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter JSON_TIMESTAMP =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ResearchProperties properties;

    private Workbook workbook;
    private Sheet sheet;
    private CellStyle headerStyle;
    private CellStyle textStyle;
    private Path outputDirectory;
    private Path xlsxFile;
    private Path csvFile;
    private Path jsonlFile;
    private Path jsonFile;
    private final List<ResearchResult> buffer = new ArrayList<>();
    private int rowCount;
    private boolean closed = true;

    public ResultWriter(ResearchProperties properties) {
        this.properties = properties;
    }

    /**
     * Cria a pasta da rodada {@code output/yyyyMMdd-HHmmss/} e os arquivos de saída.
     *
     * @return diretório da execução
     */
    public synchronized Path beginRun(String executionId) throws IOException {
        Objects.requireNonNull(executionId, "executionId");
        closeQuietly();
        buffer.clear();

        Path root = Path.of(properties.outputDirectory());
        Files.createDirectories(root);

        String folderName = LocalDateTime.now().format(RUN_FOLDER_TIMESTAMP);
        this.outputDirectory = root.resolve(folderName);
        Files.createDirectories(outputDirectory);

        this.xlsxFile = outputDirectory.resolve("results.xlsx");
        this.csvFile = outputDirectory.resolve("results.csv");
        this.jsonlFile = outputDirectory.resolve("results.jsonl");
        this.jsonFile = outputDirectory.resolve("results.json");

        this.workbook = new XSSFWorkbook();
        this.sheet = createSheet(workbook);
        this.headerStyle = createHeaderStyle(workbook);
        this.textStyle = createTextStyle(workbook);
        writeHeader();
        this.rowCount = 0;
        this.closed = false;
        flushSafely();
        return outputDirectory;
    }

    public synchronized void write(ResearchResult result) throws IOException {
        ensureOpen();
        if (rowCount >= EXCEL_MAX_ROWS) {
            throw new IOException("Limite de linhas do Excel atingido (" + EXCEL_MAX_ROWS + ").");
        }

        buffer.add(result);
        appendExcelRow(result);
        rowCount++;
        updateAutoFilter();
        flushSafely();
    }

    /** Diretório da execução ({@code output/yyyyMMdd-HHmmss}). */
    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public Path getXlsxFile() {
        return xlsxFile;
    }

    public Path getCsvFile() {
        return csvFile;
    }

    public Path getJsonlFile() {
        return jsonlFile;
    }

    public Path getJsonFile() {
        return jsonFile;
    }

    public int getRowCount() {
        return rowCount;
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        if (workbook != null) {
            flushSafely();
            workbook.close();
        }
        workbook = null;
        sheet = null;
        closed = true;
    }

    private void ensureOpen() {
        if (closed || workbook == null || sheet == null || outputDirectory == null) {
            throw new IllegalStateException("ResultWriter não inicializado. Chame beginRun() antes de escrever.");
        }
    }

    private void appendExcelRow(ResearchResult result) {
        Row row = sheet.createRow(rowCount + 1);
        row.setHeightInPoints(90);

        writeCell(row, 0, result.executionId());
        writeNumeric(row, 1, result.questionId());
        writeCell(row, 2, result.question());
        writeCell(row, 3, result.provider());
        writeCell(row, 4, result.model());
        writeNumeric(row, 5, result.repetition());
        writeCell(row, 6, truncateForExcel(result.response()));
        writeCell(row, 7, result.status());
        writeCell(row, 8, result.error());
        writeCell(row, 9, formatCellTimestamp(result));
        writeOptionalLong(row, 10, result.latencyMs());
        writeOptionalInteger(row, 11, result.inputTokens());
        writeOptionalInteger(row, 12, result.outputTokens());
    }

    private void writeHeader() {
        Row header = sheet.createRow(0);
        header.setHeightInPoints(30);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
        sheet.createFreezePane(0, 1);
        updateAutoFilter();
    }

    private void updateAutoFilter() {
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(rowCount, 0), 0, HEADERS.length - 1));
    }

    private Sheet createSheet(Workbook workbook) {
        Sheet created = workbook.createSheet("Resultados");
        created.setColumnWidth(0, 38 * 256);
        created.setColumnWidth(1, 14 * 256);
        created.setColumnWidth(2, 60 * 256);
        created.setColumnWidth(3, 16 * 256);
        created.setColumnWidth(4, 24 * 256);
        created.setColumnWidth(5, 12 * 256);
        created.setColumnWidth(6, 100 * 256);
        created.setColumnWidth(7, 12 * 256);
        created.setColumnWidth(8, 40 * 256);
        created.setColumnWidth(9, 22 * 256);
        created.setColumnWidth(10, 14 * 256);
        created.setColumnWidth(11, 16 * 256);
        created.setColumnWidth(12, 16 * 256);
        return created;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private CellStyle createTextStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private void writeCell(Row row, int index, String value) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(textStyle);
    }

    private void writeNumeric(Row row, int index, int value) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value);
        cell.setCellStyle(textStyle);
    }

    private void writeOptionalLong(Row row, int index, Long value) {
        Cell cell = row.createCell(index);
        if (value != null) {
            cell.setCellValue(value);
        } else {
            cell.setBlank();
        }
        cell.setCellStyle(textStyle);
    }

    private void writeOptionalInteger(Row row, int index, Integer value) {
        Cell cell = row.createCell(index);
        if (value != null) {
            cell.setCellValue(value);
        } else {
            cell.setBlank();
        }
        cell.setCellStyle(textStyle);
    }

    static String truncateForExcel(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= EXCEL_MAX_CELL_LENGTH) {
            return value;
        }
        String marker = "\n\n[TRUNCADO: resposta excedeu o limite de célula do Excel]";
        int maxContent = EXCEL_MAX_CELL_LENGTH - marker.length();
        return value.substring(0, maxContent) + marker;
    }

    private void flushSafely() throws IOException {
        flushXlsx();
        flushCsv();
        flushJsonl();
        flushPrettyJson();
    }

    private void flushXlsx() throws IOException {
        Path tempFile = xlsxFile.resolveSibling(xlsxFile.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(tempFile)) {
            workbook.write(output);
        }
        atomicReplace(tempFile, xlsxFile);
    }

    private void flushCsv() throws IOException {
        Path tempFile = csvFile.resolveSibling(csvFile.getFileName() + ".tmp");
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(HEADERS)
                .setRecordSeparator("\n")
                .get();

        try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (ResearchResult result : buffer) {
                printer.printRecord(
                        nullToEmpty(result.executionId()),
                        result.questionId(),
                        nullToEmpty(result.question()),
                        nullToEmpty(result.provider()),
                        nullToEmpty(result.model()),
                        result.repetition(),
                        nullToEmpty(result.response()),
                        nullToEmpty(result.status()),
                        nullToEmpty(result.error()),
                        formatCellTimestamp(result),
                        result.latencyMs() == null ? "" : result.latencyMs(),
                        result.inputTokens() == null ? "" : result.inputTokens(),
                        result.outputTokens() == null ? "" : result.outputTokens()
                );
            }
            printer.flush();
        }
        atomicReplace(tempFile, csvFile);
    }

    private void flushJsonl() throws IOException {
        Path tempFile = jsonlFile.resolveSibling(jsonlFile.getFileName() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(tempFile), StandardCharsets.UTF_8))) {
            for (ResearchResult result : buffer) {
                writer.write(toJsonObject(result, 0, false));
                writer.newLine();
            }
        }
        atomicReplace(tempFile, jsonlFile);
    }

    private void flushPrettyJson() throws IOException {
        Path tempFile = jsonFile.resolveSibling(jsonFile.getFileName() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(tempFile), StandardCharsets.UTF_8))) {
            writer.write('[');
            writer.newLine();
            for (int i = 0; i < buffer.size(); i++) {
                writer.write(toJsonObject(buffer.get(i), 1, true));
                if (i < buffer.size() - 1) {
                    writer.write(',');
                }
                writer.newLine();
            }
            writer.write(']');
            writer.newLine();
        }
        atomicReplace(tempFile, jsonFile);
    }

    /**
     * Serializa um resultado como objeto JSON.
     *
     * @param pretty se true, indenta campos (para results.json)
     */
    static String toJsonObject(ResearchResult result, int indentLevel, boolean pretty) {
        String indent = pretty ? "  ".repeat(indentLevel) : "";
        String childIndent = pretty ? "  ".repeat(indentLevel + 1) : "";
        String nl = pretty ? "\n" : "";
        String sep = pretty ? ",\n" : ",";

        StringBuilder json = new StringBuilder(256);
        json.append(indent).append('{').append(nl);
        appendJsonString(json, childIndent, "executionId", result.executionId(), pretty);
        json.append(sep);
        appendJsonNumber(json, childIndent, "questionId", result.questionId(), pretty);
        json.append(sep);
        appendJsonString(json, childIndent, "question", result.question(), pretty);
        json.append(sep);
        appendJsonString(json, childIndent, "provider", result.provider(), pretty);
        json.append(sep);
        appendJsonString(json, childIndent, "model", result.model(), pretty);
        json.append(sep);
        appendJsonNumber(json, childIndent, "repetition", result.repetition(), pretty);
        json.append(sep);
        appendJsonString(json, childIndent, "response", result.response(), pretty);
        json.append(sep);
        appendJsonString(json, childIndent, "status", result.status(), pretty);
        json.append(sep);
        appendJsonString(json, childIndent, "error", result.error(), pretty);
        json.append(sep);
        appendJsonString(json, childIndent, "timestamp", result.timestamp() == null
                ? null
                : result.timestamp().format(JSON_TIMESTAMP), pretty);
        json.append(sep);
        appendJsonNullableNumber(json, childIndent, "latencyMs", result.latencyMs(), pretty);
        json.append(sep);
        appendJsonNullableNumber(json, childIndent, "inputTokens", result.inputTokens(), pretty);
        json.append(sep);
        appendJsonNullableNumber(json, childIndent, "outputTokens", result.outputTokens(), pretty);
        json.append(nl).append(indent).append('}');
        return json.toString();
    }

    /** Compatível com testes/uso legado: objeto JSON em uma linha. */
    static String toJsonLine(ResearchResult result) {
        return toJsonObject(result, 0, false);
    }

    private static void appendJsonString(
            StringBuilder json,
            String indent,
            String field,
            String value,
            boolean pretty
    ) {
        String space = pretty ? " " : "";
        json.append(indent).append('"').append(field).append("\":").append(space);
        if (value == null) {
            json.append("null");
        } else {
            json.append('"').append(escapeJson(value)).append('"');
        }
    }

    private static void appendJsonNumber(
            StringBuilder json,
            String indent,
            String field,
            long value,
            boolean pretty
    ) {
        String space = pretty ? " " : "";
        json.append(indent).append('"').append(field).append("\":").append(space).append(value);
    }

    private static void appendJsonNullableNumber(
            StringBuilder json,
            String indent,
            String field,
            Number value,
            boolean pretty
    ) {
        String space = pretty ? " " : "";
        json.append(indent).append('"').append(field).append("\":").append(space);
        if (value == null) {
            json.append("null");
        } else {
            json.append(value);
        }
    }

    static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static String formatCellTimestamp(ResearchResult result) {
        if (result.timestamp() == null) {
            return "";
        }
        return result.timestamp().format(CELL_TIMESTAMP);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void atomicReplace(Path tempFile, Path target) throws IOException {
        try {
            Files.move(
                    tempFile,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void closeQuietly() {
        try {
            close();
        } catch (IOException ignored) {
            // reinício de execução
        }
    }
}
