package ru.mirea.avia.ui;

/** Сигнал отмены ввода: оператор ввёл 0, и операция прерывается без изменений (п. 6.10.2 ТЗ). */
public final class InputCancelledException extends RuntimeException {
    public InputCancelledException() { super("Input cancelled by operator", null, false, false); }
}
