package ru.mirea.avia.ui;

/** Сигнал закрытия потока ввода (Ctrl+D или конец файла), после которого приложение завершает работу. */
public final class InputClosedException extends RuntimeException {
    public InputClosedException() { super("Console input closed", null, false, false); }
}
