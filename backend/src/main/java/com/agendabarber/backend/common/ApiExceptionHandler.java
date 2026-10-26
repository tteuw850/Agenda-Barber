package com.agendabarber.backend.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException ignored) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request validation failed.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> invalidParameters(ConstraintViolationException ignored) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameters", "A request constraint was violated.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> invalidJson(HttpMessageNotReadableException ignored) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request body could not be read.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpectedError(Exception ignored) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "Unexpected server error.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        return ResponseEntity.status(status).body(body);
    }
}
