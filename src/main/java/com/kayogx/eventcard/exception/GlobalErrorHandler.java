package com.kayogx.eventcard.exception;

import com.kayogx.eventcard.dto.ErrorResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Turns every problem into a friendly JSON answer ({@link ErrorResponse})
 * with the right HTTP status code, so the React app can show the message directly.
 */
@RestControllerAdvice
@Slf4j
public class GlobalErrorHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotFoundException problem) {
        return ErrorResponse.of(problem.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(ConflictException problem) {
        return new ErrorResponse(problem.getMessage(), problem.getField());
    }

    @ExceptionHandler(LoginFailedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleLoginFailed(LoginFailedException problem) {
        return ErrorResponse.of(problem.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ErrorResponse handleTooManyRequests(TooManyRequestsException problem) {
        return ErrorResponse.of(problem.getMessage());
    }

    @ExceptionHandler(InvalidInputException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidInput(InvalidInputException problem) {
        return new ErrorResponse(problem.getMessage(), problem.getField());
    }

    /** A form field broke a rule such as @NotBlank or @Email. We report the first one. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidForm(MethodArgumentNotValidException problem) {
        FieldError firstError = problem.getBindingResult().getFieldErrors().get(0);
        return new ErrorResponse(firstError.getDefaultMessage(), firstError.getField());
    }

    /** The request body was not valid JSON, or had a wrong value such as an unknown role. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableRequest(HttpMessageNotReadableException problem) {
        return ErrorResponse.of("The request could not be read. Please check the values you sent.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleFileTooLarge(MaxUploadSizeExceededException problem) {
        return new ErrorResponse("The file is too large. The maximum size is 5 MB.", "file");
    }

    /** The user's role is not allowed to do this (for example a manager editing the company). */
    @ExceptionHandler({AccessDeniedException.class, NotAllowedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleNotAllowed(RuntimeException problem) {
        String message = problem instanceof NotAllowedException
                ? problem.getMessage()
                : "You are not allowed to do this.";
        return ErrorResponse.of(message);
    }

    /**
     * The database refused to save because a unique value already exists.
     * This is a safety net: services normally check for duplicates first.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicate(DataIntegrityViolationException problem) {
        log.warn("Database refused a change: {}", problem.getMostSpecificCause().getMessage());
        return ErrorResponse.of("This conflicts with existing data (for example an email or name that is already used).");
    }

    /** Anything else. We log the details but never show them to the user. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleEverythingElse(Exception problem) {
        // Spring's own web errors (unknown address, wrong HTTP method, missing file...)
        // already know their correct status code, so we keep it.
        if (problem instanceof org.springframework.web.ErrorResponse springWebError) {
            String message = springWebError.getBody().getDetail();
            return ResponseEntity.status(springWebError.getStatusCode()).body(ErrorResponse.of(message));
        }

        log.error("Unexpected error", problem);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("Something went wrong on our side. Please try again."));
    }
}
