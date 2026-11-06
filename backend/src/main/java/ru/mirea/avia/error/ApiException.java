package ru.mirea.avia.error;

import org.springframework.http.HttpStatus;

import java.util.List;

/** Базовое прикладное исключение: статус, машинный код и сообщение, безопасное для клиента. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<FieldViolation> fieldErrors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<FieldViolation> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public List<FieldViolation> getFieldErrors() { return fieldErrors; }
}
