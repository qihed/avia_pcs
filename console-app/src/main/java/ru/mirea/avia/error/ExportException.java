package ru.mirea.avia.error;

/** Сигнал ошибки создания файла экспорта (код E-601). */
public class ExportException extends AppException {
    public ExportException(String message, Throwable cause) { super(ErrorCode.E_601, message, cause); }
}
