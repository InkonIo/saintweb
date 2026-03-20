package com.schedule.app.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.schedule.app.entity.Schedule;
import com.schedule.app.entity.ScheduleEntry;
import com.schedule.app.repository.ScheduleEntryRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final ScheduleService scheduleService;
    private final ScheduleEntryRepository entryRepository;

    private static final String[] MONTHS = {
        "", "Январь","Февраль","Март","Апрель","Май","Июнь",
        "Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь"
    };

    public byte[] exportExcel(Long scheduleId) throws Exception {
        Schedule schedule = scheduleService.findById(scheduleId);
        List<ScheduleEntry> entries = entryRepository.findAllByScheduleId(scheduleId);

        // Группируем по сотруднику
        Map<Long, String> empNames = new LinkedHashMap<>();
        Map<String, String> cellMap = new HashMap<>();
        entries.forEach(e -> {
            empNames.put(e.getEmployee().getId(),
                e.getEmployee().getLastName() + " " + e.getEmployee().getFirstName());
            cellMap.put(e.getEmployee().getId() + "_" + e.getWorkDate(), e.getShiftType());
        });

        int daysCount = LocalDate.of(schedule.getYear(), schedule.getMonth(), 1)
                .lengthOfMonth();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("График");
            sheet.setColumnWidth(0, 7000);

            // Стили
            CellStyle headerStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle cellStyle = wb.createCellStyle();
            cellStyle.setAlignment(HorizontalAlignment.CENTER);
            cellStyle.setBorderBottom(BorderStyle.THIN);
            cellStyle.setBorderRight(BorderStyle.THIN);

            CellStyle titleStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            // Заголовок
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("График работы — " + MONTHS[schedule.getMonth()] + " " + schedule.getYear());
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, daysCount));

            Row subRow = sheet.createRow(1);
            subRow.createCell(0).setCellValue("Филиал: " + schedule.getBranch().getName());

            // Шапка дней
            Row headerRow = sheet.createRow(3);
            Cell empHeader = headerRow.createCell(0);
            empHeader.setCellValue("Сотрудник");
            empHeader.setCellStyle(headerStyle);

            for (int d = 1; d <= daysCount; d++) {
                Cell dayCell = headerRow.createCell(d);
                dayCell.setCellValue(d);
                dayCell.setCellStyle(headerStyle);
                sheet.setColumnWidth(d, 900);
            }

            Cell totalHeader = headerRow.createCell(daysCount + 1);
            totalHeader.setCellValue("Итого");
            totalHeader.setCellStyle(headerStyle);
            sheet.setColumnWidth(daysCount + 1, 2000);

            // Данные
            int rowNum = 4;
            for (Map.Entry<Long, String> emp : empNames.entrySet()) {
                Row row = sheet.createRow(rowNum++);
                Cell nameCell = row.createCell(0);
                nameCell.setCellValue(emp.getValue());
                nameCell.setCellStyle(cellStyle);

                int workDays = 0;
                for (int d = 1; d <= daysCount; d++) {
                    String dateStr = String.format("%d-%02d-%02d", schedule.getYear(), schedule.getMonth(), d);
                    String shift = cellMap.getOrDefault(emp.getKey() + "_" + dateStr, "");
                    Cell c = row.createCell(d);
                    c.setCellValue(shift);
                    c.setCellStyle(cellStyle);
                    if (!shift.isEmpty() && !shift.equals("В") && !shift.equals("О") && !shift.equals("Б")) {
                        workDays++;
                    }
                }
                Cell total = row.createCell(daysCount + 1);
                total.setCellValue(workDays);
                total.setCellStyle(cellStyle);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportPdf(Long scheduleId) throws Exception {
        Schedule schedule = scheduleService.findById(scheduleId);
        List<ScheduleEntry> entries = entryRepository.findAllByScheduleId(scheduleId);

        Map<Long, String> empNames = new LinkedHashMap<>();
        Map<String, String> cellMap = new HashMap<>();
        entries.forEach(e -> {
            empNames.put(e.getEmployee().getId(),
                e.getEmployee().getLastName() + " " + e.getEmployee().getFirstName());
            cellMap.put(e.getEmployee().getId() + "_" + e.getWorkDate(), e.getShiftType());
        });

        int daysCount = LocalDate.of(schedule.getYear(), schedule.getMonth(), 1).lengthOfMonth();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 20, 20, 30, 20);
        PdfWriter.getInstance(doc, out);
        doc.open();

        // Шрифт с поддержкой кириллицы
        BaseFont bf = BaseFont.createFont(
            BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(bf, 14, com.itextpdf.text.Font.BOLD);
        com.itextpdf.text.Font normalFont = new com.itextpdf.text.Font(bf, 8);
        com.itextpdf.text.Font boldFont = new com.itextpdf.text.Font(bf, 8, com.itextpdf.text.Font.BOLD);

        // Заголовок
        Paragraph title = new Paragraph(
            "Schedule: " + MONTHS[schedule.getMonth()] + " " + schedule.getYear() +
            " | Branch: " + schedule.getBranch().getName(), titleFont);
        title.setSpacingAfter(12);
        doc.add(title);

        // Таблица
        PdfPTable table = new PdfPTable(daysCount + 2);
        table.setWidthPercentage(100);

        float[] widths = new float[daysCount + 2];
        widths[0] = 8f;
        for (int i = 1; i <= daysCount; i++) widths[i] = 1f;
        widths[daysCount + 1] = 2f;
        table.setWidths(widths);

        // Шапка
        PdfPCell empH = new PdfPCell(new Phrase("Employee", boldFont));
        empH.setBackgroundColor(new BaseColor(220, 220, 220));
        empH.setPadding(4);
        table.addCell(empH);

        for (int d = 1; d <= daysCount; d++) {
            PdfPCell c = new PdfPCell(new Phrase(String.valueOf(d), boldFont));
            c.setBackgroundColor(new BaseColor(220, 220, 220));
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setPadding(2);
            table.addCell(c);
        }

        PdfPCell totalH = new PdfPCell(new Phrase("Total", boldFont));
        totalH.setBackgroundColor(new BaseColor(220, 220, 220));
        totalH.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalH.setPadding(4);
        table.addCell(totalH);

        // Строки
        boolean alt = false;
        for (Map.Entry<Long, String> emp : empNames.entrySet()) {
            BaseColor rowColor = alt ? new BaseColor(248, 248, 248) : BaseColor.WHITE;
            alt = !alt;

            PdfPCell nameCell = new PdfPCell(new Phrase(emp.getValue(), normalFont));
            nameCell.setBackgroundColor(rowColor);
            nameCell.setPadding(3);
            table.addCell(nameCell);

            int workDays = 0;
            for (int d = 1; d <= daysCount; d++) {
                String dateStr = String.format("%d-%02d-%02d", schedule.getYear(), schedule.getMonth(), d);
                String shift = cellMap.getOrDefault(emp.getKey() + "_" + dateStr, "");
                PdfPCell c = new PdfPCell(new Phrase(shift, normalFont));
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                c.setBackgroundColor(rowColor);
                c.setPadding(2);
                table.addCell(c);
                if (!shift.isEmpty() && !shift.equals("V") && !shift.equals("O") && !shift.equals("B")) {
                    workDays++;
                }
            }

            PdfPCell totalCell = new PdfPCell(new Phrase(String.valueOf(workDays), boldFont));
            totalCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            totalCell.setBackgroundColor(rowColor);
            totalCell.setPadding(3);
            table.addCell(totalCell);
        }

        doc.add(table);
        doc.close();
        return out.toByteArray();
    }
}