package ru.mirea.avia.error;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/** Единая обработка ошибок: 400 / 401 / 403 / 404 / 409, без stack trace в ответе. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest req) {
        return respond(ex.getStatus(), ex.getCode(), ex.getMessage(), req, ex.getFieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldViolation(e.getField(), e.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Проверьте заполнение полей", req, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Проверьте параметры запроса", req, violations);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class})
    ResponseEntity<ApiError> handleUnreadable(Exception ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Некорректное тело запроса", req, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Некорректное значение параметра «" + ex.getName() + "»", req, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Не указан параметр «" + ex.getParameterName() + "»", req, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Файл слишком большой", req, null);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<ApiError> handleOptimisticLock(Exception ex, HttpServletRequest req) {
        return respond(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "Запись изменена другим пользователем. Обновите данные и повторите", req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Нарушение ограничения целостности: {}", ex.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "DATA_INTEGRITY", "Операция нарушает ограничения данных", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
        return respond(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Недостаточно прав", req, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Требуется авторизация", req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ресурс не найден", req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Метод не поддерживается", req, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Тип содержимого не поддерживается", req, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Необработанная ошибка {} {}", req.getMethod(), req.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Внутренняя ошибка сервера", req, null);
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String code, String message,
                                             HttpServletRequest req, List<FieldViolation> violations) {
        return ResponseEntity.status(status)
                .body(ErrorResponseWriter.build(status, code, message, req.getRequestURI(), violations));
    }
}
