package com.omnicare.platform.shared.api;

import com.omnicare.platform.shared.domain.InvalidEmailException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions into response bodies, so no controller has to.
 *
 * <p>Bodies are a pure function of status and detail — no timestamp, no request
 * id, nothing that varies between two calls. That is what lets a failed login
 * return a byte-identical response whether the address was unknown or the
 * password was wrong.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<Map<String, Object>> onApplicationException(ApplicationException e) {
        return problem(e.status(), e.getMessage());
    }

    @ExceptionHandler(InvalidEmailException.class)
    ResponseEntity<Map<String, Object>> onInvalidEmail(InvalidEmailException e) {
        return problem(HttpStatus.BAD_REQUEST, "email must be a valid address");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> onValidationFailure(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse("Request is not valid");
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("detail", detail);
        return ResponseEntity.status(status).body(body);
    }
}
