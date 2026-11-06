package ru.mirea.avia.error;

import org.springframework.http.HttpStatus;

/** HTTP 404. */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public NotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
