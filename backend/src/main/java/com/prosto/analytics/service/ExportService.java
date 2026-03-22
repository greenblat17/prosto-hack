package com.prosto.analytics.service;

import com.prosto.analytics.dto.PivotResultDto;
import com.prosto.analytics.dto.PivotResultRowDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExportService {

    public byte[] exportCsv(PivotResultDto result) {
        var out = new ByteArrayOutputStream();
        var writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        writer.write('\ufeff');

        if (result.rows().isEmpty()) {
            writer.flush();
            return out.toByteArray();
        }

        List<String> valueColumns = new ArrayList<>(result.rows().getFirst().values().keySet());
        int keyCount = result.rows().getFirst().keys().size();

        writeCsvHeader(writer, keyCount, valueColumns);
        writeCsvRows(writer, result.rows(), valueColumns);
        writeCsvTotals(writer, result, keyCount, valueColumns);

        writer.flush();
        return out.toByteArray();
    }

    private void writeCsvHeader(PrintWriter writer, int keyCount, List<String> valueColumns) {
        var header = new ArrayList<String>();
        for (int i = 0; i < keyCount; i++) {
            header.add("Ключ " + (i + 1));
        }
        header.addAll(valueColumns);
        writer.println(String.join(";", header.stream().map(this::escapeCsv).toList()));
    }

    private void writeCsvRows(PrintWriter writer, List<PivotResultRowDto> rows, List<String> valueColumns) {
        for (PivotResultRowDto row : rows) {
            var line = new ArrayList<String>();
            for (String key : row.keys()) {
                line.add(escapeCsv(key));
            }
            for (String col : valueColumns) {
                Object val = row.values().get(col);
                line.add(val != null ? escapeCsv(String.valueOf(val)) : "");
            }
            writer.println(String.join(";", line));
        }
    }

    private void writeCsvTotals(PrintWriter writer, PivotResultDto result, int keyCount, List<String> valueColumns) {
        if (result.totals() == null || result.totals().isEmpty()) return;

        var totalsLine = new ArrayList<String>();
        totalsLine.add(escapeCsv("ИТОГО"));
        for (int i = 1; i < keyCount; i++) {
            totalsLine.add("");
        }
        for (String col : valueColumns) {
            Object val = result.totals().get(col);
            totalsLine.add(val != null ? escapeCsv(String.valueOf(val)) : "");
        }
        writer.println(String.join(";", totalsLine));
    }

    public byte[] exportExcel(PivotResultDto result) throws IOException {
        try (var workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Сводная таблица");

            if (result.rows().isEmpty()) {
                return toBytes(workbook);
            }

            List<String> valueColumns = new ArrayList<>(result.rows().getFirst().values().keySet());
            int keyCount = result.rows().getFirst().keys().size();

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle totalsStyle = createTotalsStyle(workbook);

            writeExcelHeader(sheet, keyCount, valueColumns, headerStyle);
            int rowIdx = writeExcelDataRows(sheet, result.rows(), valueColumns, numberStyle);
            writeExcelTotals(sheet, result, keyCount, valueColumns, totalsStyle, rowIdx);

            int totalCols = keyCount + valueColumns.size();
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, totalCols - 1));
            for (int i = 0; i < totalCols; i++) {
                sheet.autoSizeColumn(i);
            }

            return toBytes(workbook);
        }
    }

    private void writeExcelHeader(Sheet sheet, int keyCount, List<String> valueColumns, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        int col = 0;
        for (int i = 0; i < keyCount; i++) {
            Cell cell = headerRow.createCell(col++);
            cell.setCellValue("Ключ " + (i + 1));
            cell.setCellStyle(headerStyle);
        }
        for (String valCol : valueColumns) {
            Cell cell = headerRow.createCell(col++);
            cell.setCellValue(valCol);
            cell.setCellStyle(headerStyle);
        }
    }

    private int writeExcelDataRows(Sheet sheet, List<PivotResultRowDto> rows,
                                   List<String> valueColumns, CellStyle numberStyle) {
        int rowIdx = 1;
        for (PivotResultRowDto row : rows) {
            Row excelRow = sheet.createRow(rowIdx++);
            int col = 0;
            for (String key : row.keys()) {
                excelRow.createCell(col++).setCellValue(key);
            }
            for (String valCol : valueColumns) {
                Cell cell = excelRow.createCell(col++);
                setCellValue(cell, row.values().get(valCol), numberStyle);
            }
        }
        return rowIdx;
    }

    private void writeExcelTotals(Sheet sheet, PivotResultDto result, int keyCount,
                                  List<String> valueColumns, CellStyle totalsStyle, int rowIdx) {
        if (result.totals() == null || result.totals().isEmpty()) return;

        Row totalsRow = sheet.createRow(rowIdx);
        Cell labelCell = totalsRow.createCell(0);
        labelCell.setCellValue("ИТОГО");
        labelCell.setCellStyle(totalsStyle);

        int col = keyCount;
        for (String valCol : valueColumns) {
            Cell cell = totalsRow.createCell(col++);
            setCellValue(cell, result.totals().get(valCol), null);
            cell.setCellStyle(totalsStyle);
        }
    }

    private void setCellValue(Cell cell, Object val, CellStyle numberStyle) {
        if (val instanceof Number n) {
            cell.setCellValue(n.doubleValue());
            if (numberStyle != null) cell.setCellStyle(numberStyle);
        } else if (val != null) {
            cell.setCellValue(val.toString());
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private CellStyle createTotalsStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setBorderTop(BorderStyle.DOUBLE);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private byte[] toBytes(Workbook workbook) throws IOException {
        var out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
