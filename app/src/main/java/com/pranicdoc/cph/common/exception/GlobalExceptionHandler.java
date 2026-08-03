package com.pranicdoc.cph.common.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler → RFC 7807 ProblemDetail responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        pd.setType(URI.create("https://pranicdoc.com/errors/not-found"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(ConsentRequiredException.class)
    public ProblemDetail handleConsentRequired(ConsentRequiredException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Consent Required");
        pd.setType(URI.create("https://pranicdoc.com/errors/consent-required"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(SessionIncompleteException.class)
    public ProblemDetail handleSessionIncomplete(SessionIncompleteException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setTitle("Session Incomplete");
        pd.setType(URI.create("https://pranicdoc.com/errors/session-incomplete"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setTitle("Validation Error");
        pd.setType(URI.create("https://pranicdoc.com/errors/validation"));
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("errors", fieldErrors);
        return pd;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
        pd.setTitle("Forbidden");
        pd.setType(URI.create("https://pranicdoc.com/errors/forbidden"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * A missing required @RequestParam (e.g. publish()'s consentType) must surface
     * as a clean 400, not fall through to the catch-all 500 — Spring throws this
     * before the handler body ever runs, so it is functionally identical to a
     * validation failure from the caller's perspective.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Bad Request");
        pd.setType(URI.create("https://pranicdoc.com/errors/bad-request"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Bad Request");
        pd.setType(URI.create("https://pranicdoc.com/errors/bad-request"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ProblemDetail handleTokenExpired(TokenExpiredException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setTitle("Token Used or Expired");
        pd.setType(URI.create("https://pranicdoc.com/errors/token-expired"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(FeedbackAlreadySubmittedException.class)
    public ProblemDetail handleFeedbackAlreadySubmitted(FeedbackAlreadySubmittedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Feedback Already Submitted");
        pd.setType(URI.create("https://pranicdoc.com/errors/feedback-already-submitted"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(InsufficientCreditsException.class)
    public ProblemDetail handleInsufficientCredits(InsufficientCreditsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
        pd.setTitle("Insufficient Credits");
        pd.setType(URI.create("https://pranicdoc.com/errors/insufficient-credits"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ProblemDetail handleRateLimitExceeded(RateLimitExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        pd.setTitle("Rate Limit Exceeded");
        pd.setType(URI.create("https://pranicdoc.com/errors/rate-limit-exceeded"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ProblemDetail handleNoHandlerFound(NoHandlerFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "No endpoint " + ex.getHttpMethod() + " " + ex.getRequestURL());
        pd.setTitle("Not Found");
        pd.setType(URI.create("https://pranicdoc.com/errors/not-found"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * T-31-03: a concurrent publish for the same consentType races on the V25 unique
     * constraint uq_policy_version_type_ver. Rather than falling through to the
     * catch-all 500, surface this loudly and cleanly as a 409 so the client knows
     * to reload and retry instead of assuming the request silently corrupted state.
     * Detail text is fixed (never ex.getMessage()) — Hibernate/Postgres constraint
     * violation messages leak table/column/constraint names (T-31-09).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The requested change conflicts with existing data. It may have been modified concurrently — reload and retry.");
        pd.setTitle("Conflict");
        pd.setType(URI.create("https://pranicdoc.com/errors/conflict"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * T-31-05: an oversized policy upload to the publish endpoint must not return a
     * raw 500 or leak a stack trace. Detail text is fixed (never ex.getMessage()) —
     * this project deliberately does not raise Spring Boot's default multipart
     * limits here, it only makes the resulting exception legible (T-31-09).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "The uploaded file is too large.");
        pd.setTitle("Payload Too Large");
        pd.setType(URI.create("https://pranicdoc.com/errors/payload-too-large"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setTitle("Internal Server Error");
        pd.setType(URI.create("https://pranicdoc.com/errors/internal"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
