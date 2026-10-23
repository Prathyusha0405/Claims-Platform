package com.chubb.claims.assessment.exception;

import com.chubb.claims.assessment.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One JSON error shape for every failure. Extending {@link ResponseEntityExceptionHandler} gives correct status
 * codes for Spring MVC's own exceptions (400 bad parameter, 404 unknown path, 405, 415, ...); they are rendered
 * through {@link #handleExceptionInternal}. Claim-service failures keep claim-service's own status and message where
 * that is meaningful to the staff member (404, 409, 403, 400); trouble reaching it is a 503 or 502.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), messageFor(error)));
        return respond(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Malformed request body", request, null, headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String message = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail()
                : status.getReasonPhrase();
        return respond(status, message, request, null, headers);
    }

    @ExceptionHandler(ClaimNotFoundException.class)
    public ResponseEntity<Object> handleClaimNotFound(ClaimNotFoundException ex, WebRequest request) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), request, null, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(ClaimConflictException.class)
    public ResponseEntity<Object> handleClaimConflict(ClaimConflictException ex, WebRequest request) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), request, null, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(ClaimAccessDeniedException.class)
    public ResponseEntity<Object> handleClaimAccessDenied(ClaimAccessDeniedException ex, WebRequest request) {
        return respond(HttpStatus.FORBIDDEN, ex.getMessage(), request, null, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Object> handleInvalidRequest(InvalidRequestException ex, WebRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(ClaimServiceUnavailableException.class)
    public ResponseEntity<Object> handleClaimServiceUnavailable(ClaimServiceUnavailableException ex, WebRequest request) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request, null, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(ClaimServiceProtocolException.class)
    public ResponseEntity<Object> handleClaimServiceProtocol(ClaimServiceProtocolException ex, WebRequest request) {
        return respond(HttpStatus.BAD_GATEWAY, ex.getMessage(), request, null, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleConcurrentModification(OptimisticLockingFailureException ex, WebRequest request) {
        return respond(HttpStatus.CONFLICT,
                "The assessment was changed by another request; reload it and try again", request, null, HttpHeaders.EMPTY);
    }

    /** Last resort: log the cause, tell the client nothing about internals. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception on {}", pathOf(request), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, null, HttpHeaders.EMPTY);
    }

    /**
     * Bean-validation messages are already client-friendly. Binding failures (wrong enum value, bad number, ...) carry
     * Spring's conversion message, which names internal Java classes, so those are rewritten.
     */
    private static String messageFor(FieldError error) {
        if (!error.isBindingFailure()) {
            return error.getDefaultMessage();
        }
        String message = "Invalid value '" + abbreviate(String.valueOf(error.getRejectedValue())) + "'";
        Class<?> requiredType = requiredTypeOf(error);
        if (requiredType != null && requiredType.isEnum()) {
            message += "; allowed values: " + Arrays.toString(requiredType.getEnumConstants());
        }
        return message;
    }

    private static Class<?> requiredTypeOf(FieldError error) {
        try {
            return error.unwrap(TypeMismatchException.class).getRequiredType();
        } catch (IllegalArgumentException notATypeMismatch) {
            return null;
        }
    }

    private static String abbreviate(String value) {
        return value.length() <= 50 ? value : value.substring(0, 50) + "...";
    }

    private ResponseEntity<Object> respond(HttpStatus status, String message, WebRequest request,
                                           Map<String, String> fieldErrors, HttpHeaders headers) {
        return ResponseEntity.status(status)
                .headers(headers)
                .body(ErrorResponse.of(status, message, pathOf(request), fieldErrors));
    }

    private static String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI() : "";
    }
}
