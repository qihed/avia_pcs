package ru.mirea.avia.service;

import ru.mirea.avia.domain.DocumentType;
import ru.mirea.avia.dto.PageResponse;
import ru.mirea.avia.dto.TableDtos.DatabaseTable;
import ru.mirea.avia.dto.TableDtos.TablePage;
import ru.mirea.avia.jdbc.Transactions;
import ru.mirea.avia.repository.TableViewRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Постраничный просмотр таблиц базы данных в исходном виде (FR-18, UC-12).
 *
 * <p>Номер документа маскируется и здесь: полный номер доступен только в детальной
 * карточке пассажира (NFR-09).</p>
 */
public class TableViewService {
    public static final int PAGE_SIZE = 20;
    private static final String DOCUMENT_TYPE_COLUMN = "document_type";
    private static final String DOCUMENT_NUMBER_COLUMN = "document_number";

    private final TableViewRepository tables;
    private final Transactions transactions;

    public TableViewService(TableViewRepository tables, Transactions transactions) {
        this.tables = tables;
        this.transactions = transactions;
    }

    /** Возвращает страницу таблицы (нумерация с нуля) с маскированными номерами документов. */
    public TablePage page(DatabaseTable table, int page) {
        TablePage result = transactions.read(() -> tables.findPage(table, Math.max(0, page), PAGE_SIZE));
        int typeIndex = result.columns().indexOf(DOCUMENT_TYPE_COLUMN);
        int numberIndex = result.columns().indexOf(DOCUMENT_NUMBER_COLUMN);
        if (typeIndex < 0 || numberIndex < 0) return result;
        PageResponse<List<String>> rows = result.rows();
        List<List<String>> masked = rows.content().stream()
                .map(row -> maskRow(row, typeIndex, numberIndex))
                .toList();
        return new TablePage(result.table(), result.columns(), new PageResponse<>(masked, rows.page(),
                rows.size(), rows.totalElements(), rows.totalPages(), rows.first(), rows.last()));
    }

    private static List<String> maskRow(List<String> row, int typeIndex, int numberIndex) {
        List<String> copy = new ArrayList<>(row);
        DocumentType type = DocumentType.valueOf(row.get(typeIndex));
        copy.set(numberIndex, type.mask(row.get(numberIndex)));
        return copy;
    }
}
