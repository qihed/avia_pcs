package ru.mirea.avia.repository;

import ru.mirea.avia.dto.TableDtos.DatabaseTable;
import ru.mirea.avia.dto.TableDtos.TablePage;

/** Persistence gateway for raw paged table output (FR-18). */
public interface TableViewRepository {
    TablePage findPage(DatabaseTable table, int page, int size);
}
