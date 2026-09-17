package ru.mirea.avia.export;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Стили и типизированные ячейки листов Excel (приложение Г ТЗ).
 *
 * <p>Заголовок полужирный, с заливкой и выравниванием по центру; строка заголовков закреплена,
 * включён автофильтр, ширина столбцов подбирается по содержимому. Числа, суммы и даты
 * записываются значениями с форматом отображения, а не текстом, поэтому в Excel работают
 * сортировка и формулы.</p>
 */
final class ExcelStyles {
    private static final int CHAR_WIDTH = 256;
    private static final int MAX_COLUMN_WIDTH = 60 * CHAR_WIDTH;
    private static final int WIDTH_PADDING = 2 * CHAR_WIDTH;
    private static final int MIN_WIDTH_CHARS = 8;

    private final CellStyle headerStyle;
    private final CellStyle boldStyle;
    private final CellStyle quotedStyle;
    private final CellStyle integerStyle;
    private final CellStyle moneyStyle;
    private final CellStyle percentStyle;
    private final CellStyle dateTimeStyle;
    private final CellStyle dateStyle;

    ExcelStyles(Workbook workbook) {
        DataFormat format = workbook.createDataFormat();
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        this.headerStyle = createHeaderStyle(workbook, boldFont);
        this.boldStyle = createBoldStyle(workbook, boldFont);
        this.quotedStyle = createQuotedStyle(workbook);
        this.integerStyle = createFormatStyle(workbook, format, "0");
        this.moneyStyle = createFormatStyle(workbook, format, "#,##0.00");
        this.percentStyle = createFormatStyle(workbook, format, "0.0\" %\"");
        this.dateTimeStyle = createFormatStyle(workbook, format, "dd.mm.yyyy hh:mm");
        this.dateStyle = createFormatStyle(workbook, format, "dd.mm.yyyy");
    }

    /** Записывает строку заголовков в первую строку листа. */
    void header(Sheet sheet, List<String> titles) {
        Row row = sheet.createRow(0);
        for (int column = 0; column < titles.size(); column++) {
            styled(row, column, headerStyle).setCellValue(titles.get(column));
        }
    }

    /** Закрепляет строку заголовков, включает автофильтр и подбирает ширину столбцов. */
    void finishTable(Sheet sheet, int columnCount, int lastRow) {
        sheet.createFreezePane(0, 1);
        // Диапазон автофильтра должен содержать хотя бы одну строку данных даже у пустого листа.
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(lastRow, 1), 0, columnCount - 1));
        autoSize(sheet, columnCount);
    }

    /** Подбирает ширину столбцов по содержимому, но не шире 60 символов. */
    void autoSize(Sheet sheet, int columnCount) {
        for (int column = 0; column < columnCount; column++) {
            try {
                sheet.autoSizeColumn(column);
                int width = Math.min(sheet.getColumnWidth(column) + WIDTH_PADDING, MAX_COLUMN_WIDTH);
                sheet.setColumnWidth(column, width);
            } catch (RuntimeException | InternalError | UnsatisfiedLinkError | NoClassDefFoundError ex) {
                // В системе без шрифтов (headless-сервер) autoSizeColumn недоступен,
                // поэтому ширина рассчитывается по числу символов в ячейках.
                sheet.setColumnWidth(column, estimateWidth(sheet, column));
            }
        }
    }

    /** Записывает текст; значение, похожее на формулу, получает признак текста Excel. */
    void text(Row row, int column, String value) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        // Строка XLSX не вычисляется, а признак «'» в стиле сохраняет её текстом и при правке в Excel.
        if (AbstractFileExporter.startsLikeFormula(value)) cell.setCellStyle(quotedStyle);
    }

    void bold(Row row, int column, String value) {
        styled(row, column, boldStyle).setCellValue(value);
    }

    void number(Row row, int column, long value) {
        styled(row, column, integerStyle).setCellValue(value);
    }

    void money(Row row, int column, BigDecimal value) {
        styled(row, column, moneyStyle).setCellValue(value.doubleValue());
    }

    void percent(Row row, int column, BigDecimal value) {
        styled(row, column, percentStyle).setCellValue(value.doubleValue());
    }

    void dateTime(Row row, int column, LocalDateTime value) {
        styled(row, column, dateTimeStyle).setCellValue(value);
    }

    void date(Row row, int column, LocalDate value) {
        styled(row, column, dateStyle).setCellValue(value);
    }

    private static Cell styled(Row row, int column, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        return cell;
    }

    private static int estimateWidth(Sheet sheet, int column) {
        int maxChars = MIN_WIDTH_CHARS;
        for (Row row : sheet) {
            Cell cell = row.getCell(column);
            if (cell != null) {
                maxChars = Math.max(maxChars, cell.toString().length());
            }
        }
        return Math.min((maxChars + 2) * CHAR_WIDTH, MAX_COLUMN_WIDTH);
    }

    private static CellStyle createHeaderStyle(Workbook workbook, Font boldFont) {
        CellStyle style = workbook.createCellStyle();
        style.setFont(boldFont);
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    private static CellStyle createBoldStyle(Workbook workbook, Font boldFont) {
        CellStyle style = workbook.createCellStyle();
        style.setFont(boldFont);
        return style;
    }

    private static CellStyle createQuotedStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setQuotePrefixed(true);
        return style;
    }

    private static CellStyle createFormatStyle(Workbook workbook, DataFormat format, String pattern) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(format.getFormat(pattern));
        return style;
    }
}
