package ru.mirea.avia.fx.controller;

import javafx.scene.control.TableCell;

import java.util.function.Function;

/** Общие ячейки таблиц: значение в колонке остаётся типизированным (дата, сумма) и сортируется по значению. */
public final class TableCells {
    private TableCells() { }

    /** Ячейка, которая показывает значение через {@code format}; пустая строка таблицы — пустая ячейка. */
    public static <S, T> TableCell<S, T> text(Function<T, String> format) {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.apply(item));
            }
        };
    }
}
