package com.codepill.catalog.adapter.in.web;

import com.codepill.catalog.application.PillNotFoundException;
import com.codepill.catalog.application.SlugAlreadyInUseException;
import com.codepill.catalog.domain.DomainValidationException;
import com.codepill.catalog.domain.PillLifecycleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central mapping of domain/application errors to RFC 9457
 * {@code application/problem+json} (ARCHITECTURE.md §3.1 rule 7). Responses
 * never leak internals (SECURITY.md §3.2 rule 2); details land in logs with
 * trace correlation instead.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PillNotFoundException.class)
    ProblemDetail handleNotFound(PillNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Pill not found", "pill does not exist");
    }

    @ExceptionHandler(SlugAlreadyInUseException.class)
    ProblemDetail handleSlugConflict(SlugAlreadyInUseException e) {
        return problem(HttpStatus.CONFLICT, "Slug already in use", e.getMessage());
    }

    @ExceptionHandler(PillLifecycleException.class)
    ProblemDetail handleLifecycle(PillLifecycleException e) {
        return problem(HttpStatus.CONFLICT, "Operation not allowed in current state", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "the pill was modified concurrently; reload and retry");
    }

    @ExceptionHandler(DomainValidationException.class)
    ProblemDetail handleDomainValidation(DomainValidationException e) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBeanValidation(MethodArgumentNotValidException e) {
        var problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "request body is invalid");
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail handleUnreadable(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", "request could not be parsed");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", "not allowed to perform this operation");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("unhandled exception", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error",
                "an unexpected error occurred");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
