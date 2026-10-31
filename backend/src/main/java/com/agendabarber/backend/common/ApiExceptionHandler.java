package com.agendabarber.backend.common;

import com.agendabarber.backend.appointments.AppointmentService;
import com.agendabarber.backend.auth.AuthService;
import com.agendabarber.backend.finance.ExpenseService;
import com.agendabarber.backend.auth.AuthSessionService;
import com.agendabarber.backend.auth.LoginRateLimiter;
import com.agendabarber.backend.servicecatalog.ServiceCatalogService;
import com.agendabarber.backend.clientcatalog.ClientCatalogService;
import com.agendabarber.backend.scheduleconfig.ScheduleConfigService;
import com.agendabarber.backend.scheduling.AgendaSchedulingService;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

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

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(AccessDeniedException ignored) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "Insufficient access.");
    }

    @ExceptionHandler(AuthService.InvalidLoginException.class)
    ResponseEntity<ProblemDetail> invalidCredentials(AuthService.InvalidLoginException ignored) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid credentials.");
    }

    @ExceptionHandler(AuthSessionService.InvalidSessionException.class)
    ResponseEntity<ProblemDetail> invalidSession(AuthSessionService.InvalidSessionException ignored) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid or expired session.");
    }

    @ExceptionHandler(LoginRateLimiter.RateLimitedException.class)
    ResponseEntity<ProblemDetail> tooManyAttempts(LoginRateLimiter.RateLimitedException ignored) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many login attempts. Try again later.");
        body.setTitle("Too many requests");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "900").body(body);
    }

    @ExceptionHandler(ServiceCatalogService.ServiceNotFoundException.class)
    ResponseEntity<ProblemDetail> unknownService(ServiceCatalogService.ServiceNotFoundException ignored) {
        return problem(HttpStatus.NOT_FOUND, "Not found", "Service not found.");
    }

    @ExceptionHandler(ServiceCatalogService.InvalidServiceUpdateException.class)
    ResponseEntity<ProblemDetail> invalidService(ServiceCatalogService.InvalidServiceUpdateException ignored) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Invalid service data.");
    }

    @ExceptionHandler(ClientCatalogService.ClientNotFoundException.class)
    ResponseEntity<ProblemDetail> unknownClient(ClientCatalogService.ClientNotFoundException ignored) {
        return problem(HttpStatus.NOT_FOUND, "Not found", "Client not found.");
    }

    @ExceptionHandler(ClientCatalogService.InvalidClientException.class)
    ResponseEntity<ProblemDetail> invalidClient(ClientCatalogService.InvalidClientException ignored) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Invalid client data.");
    }

    @ExceptionHandler(ClientCatalogService.DuplicatePhoneException.class)
    ResponseEntity<ProblemDetail> duplicatePhone(ClientCatalogService.DuplicatePhoneException ignored) {
        return problem(HttpStatus.CONFLICT, "Conflict", "Phone already registered.");
    }

    @ExceptionHandler(ScheduleConfigService.InvalidScheduleException.class)
    ResponseEntity<ProblemDetail> invalidSchedule(ScheduleConfigService.InvalidScheduleException ignored) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Invalid scheduling settings.");
    }

    @ExceptionHandler(ScheduleConfigService.ScheduleNotFoundException.class)
    ResponseEntity<ProblemDetail> missingSchedule(ScheduleConfigService.ScheduleNotFoundException ignored) {
        return problem(HttpStatus.NOT_FOUND, "Not found", "Scheduling item not found.");
    }

    @ExceptionHandler({ScheduleConfigService.ScheduleConflictException.class,
                       AgendaSchedulingService.ScheduleConflictException.class})
    ResponseEntity<ProblemDetail> schedulingConflict(RuntimeException ignored) {
        return problem(HttpStatus.CONFLICT, "Conflict", "Requested time is unavailable.");
    }

    @ExceptionHandler(ExpenseService.InvalidExpenseException.class)
    ResponseEntity<ProblemDetail> invalidExpense(ExpenseService.InvalidExpenseException ignored) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Invalid expense data.");
    }

    @ExceptionHandler(ExpenseService.ExpenseNotFoundException.class)
    ResponseEntity<ProblemDetail> missingExpense(ExpenseService.ExpenseNotFoundException ignored) {
        return problem(HttpStatus.NOT_FOUND, "Not found", "Expense not found.");
    }

    @ExceptionHandler(ExpenseService.ExpenseConflictException.class)
    ResponseEntity<ProblemDetail> conflictingExpense(ExpenseService.ExpenseConflictException ignored) {
        return problem(HttpStatus.CONFLICT, "Conflict", "Expense idempotency conflict.");
    }

    @ExceptionHandler(com.agendabarber.backend.reports.ReportService.InvalidReportException.class)
    ResponseEntity<ProblemDetail> invalidReport(
            com.agendabarber.backend.reports.ReportService.InvalidReportException ignored) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Invalid reporting period or limit.");
    }

    @ExceptionHandler(com.agendabarber.backend.reports.ReportService.ReportNotFoundException.class)
    ResponseEntity<ProblemDetail> missingReportOwner(
            com.agendabarber.backend.reports.ReportService.ReportNotFoundException ignored) {
        return problem(HttpStatus.NOT_FOUND, "Not found", "Barber not found.");
    }

    @ExceptionHandler(AppointmentService.AppointmentNotFoundException.class)
    ResponseEntity<ProblemDetail> missingAppointment(AppointmentService.AppointmentNotFoundException ignored) {
        return problem(HttpStatus.NOT_FOUND, "Not found", "Appointment or selection not found.");
    }

    @ExceptionHandler(AppointmentService.InvalidAppointmentException.class)
    ResponseEntity<ProblemDetail> invalidAppointment(AppointmentService.InvalidAppointmentException ignored) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Invalid appointment data.");
    }

    @ExceptionHandler(AppointmentService.AppointmentConflictException.class)
    ResponseEntity<ProblemDetail> conflictingAppointment(AppointmentService.AppointmentConflictException ignored) {
        return problem(HttpStatus.CONFLICT, "Conflict", "Appointment conflict.");
    }

    @ExceptionHandler({MissingRequestHeaderException.class,
                       MissingServletRequestParameterException.class,
                       MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> missingOrInvalidParameter(Exception ignored) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Required parameter is invalid.");
    }

    @ExceptionHandler({CannotCreateTransactionException.class,
                       DataAccessResourceFailureException.class,
                       TransientDataAccessException.class,
                       ReadRetryExecutor.UnavailableAfterRetries.class})
    ResponseEntity<ProblemDetail> unavailableDatabase(RuntimeException ignored) {
        String sqlState = "unknown";
        for (Throwable cause = ignored; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                sqlState = sql.getSQLState();
                break;
            }
        }
        LOGGER.warn("event=database_unavailable exception={} sqlstate={}",
                ignored.getClass().getSimpleName(), sqlState);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(unavailableBody());
    }

    private ProblemDetail unavailableBody() {
        ProblemDetail body = problemBody(HttpStatus.SERVICE_UNAVAILABLE,
                "Service unavailable", "Storage temporarily unavailable.");
        body.setProperty("code", "DB_TEMPORARILY_UNAVAILABLE");
        return body;
    }

    private ProblemDetail problemBody(HttpStatus status, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        return body;
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
