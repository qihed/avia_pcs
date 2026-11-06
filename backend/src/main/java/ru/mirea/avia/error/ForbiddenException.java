package ru.mirea.avia.error;

import org.springframework.http.HttpStatus;

/** HTTP 403. */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
