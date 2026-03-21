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

        var header = new ArrayList<String>();
        int keyCount = result.rows().getFirst().keys().size();
        for (int i = 0; i < keyCount; i++) {
            header.add("Ключ " + (i + 1));
        }
        header.addAll(valueColumns);
        writer.println(String.join(";", header.stream().map(this::escapeCsv).toList()));

        for (PivotResultRowDto row : result.rows()) {
            var line = new ArrayList<String>();
            for (String key : row.keys()) {
                line.add(escapeCsv(key));
            }
            for (String col : valueColumns) {
                Double val = row.values().get(col);
                line.add(val != null ? String.valueOf(val) : "");
            }
            writer.println(String.join(";", line));
        }

        if (result.totals() != null && !result.totals().isEmpty()) {
            var totalsLine = new ArrayList<String>();
            totalsLine.add(escapeCsv("ИТОГО"));
            for (int i = 1; i < keyCount; i++) {
                totalsLine.add("");
            }
            for (String col : valueColumns) {
                Double val = result.totals().get(col);
                totalsLine.add(val != null ? String.valueOf(val) : "");
            }
            writer.println(String.join(";", totalsLine));
        }

        writer.flush();
        return out.toByteArray();
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

            int rowIdx = 1;
            for (PivotResultRowDto row : result.rows()) {
                Row excelRow = sheet.createRow(rowIdx++);
                col = 0;
                for (String key : row.keys()) {
                    excelRow.createCell(col++).setCellValue(key);
                }
                for (String valCol : valueColumns) {
                    Double val = row.values().get(valCol);
                    Cell cell = excelRow.createCell(col++);
                    if (val != null) {
                        cell.setCellValue(val);
                        cell.setCellStyle(numberStyle);
                    }
                }
            }

            if (result.totals() != null && !result.totals().isEmpty()) {
                Row totalsRow = sheet.createRow(rowIdx);
                Cell labelCell = totalsRow.createCell(0);
                labelCell.setCellValue("ИТОГО");
                labelCell.setCellStyle(totalsStyle);

                col = keyCount;
                for (String valCol : valueColumns) {
                    Double val = result.totals().get(valCol);
                    Cell cell = totalsRow.createCell(col++);
                    if (val != null) {
                        cell.setCellValue(val);
                    }
                    cell.setCellStyle(totalsStyle);
                }
            }

            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, keyCount + valueColumns.size() - 1));
            for (int i = 0; i < keyCount + valueColumns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            return toBytes(workbook);
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
