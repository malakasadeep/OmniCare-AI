package com.omnicare.platform.shared.api;

import com.omnicare.platform.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import com.omnicare.platform.shared.domain.InvalidEmailException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

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
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, detail.isEmpty() ? "Request is not valid" : detail);
    }

    /** Query and path parameter constraints — {@code @Min}, {@code @Max} on a controller. */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, Object>> onParameterConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, detail.isEmpty() ? "Request is not valid" : detail);
    }

    /** A path variable or query parameter that is not the declared type, such as a malformed UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, Object>> onTypeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getName() + " is not a valid value");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> onUnreadableBody(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }

    /**
     * A domain rule was broken in a way no controller anticipated. 400 rather
     * than 500: the domain rejected the input, so the request was wrong, not the
     * server.
     */
    @ExceptionHandler(DomainException.class)
    ResponseEntity<Map<String, Object>> onDomainRule(DomainException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * Anything Spring itself raised that already carries a status —
     * {@code ResponseStatusException} and, through it,
     * {@code NoResourceFoundException} for an unmapped path. Without this the
     * catch-all below would swallow them and report 500 for what is really a
     * 404, which is exactly what happened the first time it was added.
     *
     * <p>Both types are listed because they do not share a supertype that is
     * also a {@code Throwable}: {@code ResponseStatusException} extends
     * {@code ErrorResponseException}, while {@code NoResourceFoundException}
     * extends {@code ServletException} and only <em>implements</em>
     * {@code ErrorResponse}. Handling the exception class alone silently misses
     * the unmapped-path case.
     *
     * <p>The body says only the status phrase. These exceptions carry reasons
     * written for a developer, and some of them echo the requested path back.
     */
    @ExceptionHandler({ErrorResponseException.class, NoResourceFoundException.class})
    ResponseEntity<Map<String, Object>> onSpringErrorResponse(Exception e) {
        HttpStatus status = e instanceof ErrorResponse response
                ? HttpStatus.valueOf(response.getStatusCode().value())
                : HttpStatus.INTERNAL_SERVER_ERROR;
        return problem(status, status.getReasonPhrase());
    }

    /**
     * An I/O failure while writing the response.
     *
     * <p>Almost always the visitor going away — closed the tab, navigated off,
     * lost signal — which is ordinary, especially mid-stream, and not a fault.
     * The discriminator is whether the response is already committed: if it is,
     * bytes were already on the wire and there is no connection left to explain
     * anything to, so this is logged at debug and nothing is written. Attempting
     * to write would fail a second time, inside the handler, trying to put a
     * JSON body into a response already committed as {@code text/event-stream}.
     *
     * <p>An I/O failure <em>before</em> anything was committed is a real
     * server-side problem and still reported as one.
     *
     * <p>Catching {@code IOException} rather than {@code ClientAbortException}
     * is deliberate: on some platforms the raw {@code IOException} propagates
     * without ever being wrapped in Tomcat's type, so naming that type alone
     * misses the very case this exists for.
     */
    @ExceptionHandler({IOException.class, AsyncRequestNotUsableException.class})
    ResponseEntity<Map<String, Object>> onWriteFailure(Exception e, HttpServletResponse response) {
        if (response.isCommitted() || e instanceof AsyncRequestNotUsableException) {
            log.debug("Client went away before the response completed: {}", e.getMessage());
            // Null, not an empty body: the response is finished either way, and
            // Spring treats this as handled without trying to write.
            return null;
        }
        log.error("I/O failure while writing a response", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
    }

    /**
     * The catch-all. Logs the cause with a stack trace and tells the caller
     * nothing about it — an exception message can carry a table name, a file
     * path or a fragment of a query.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> onUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
    }

    private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("detail", detail);
        return ResponseEntity.status(status).body(body);
    }
}
