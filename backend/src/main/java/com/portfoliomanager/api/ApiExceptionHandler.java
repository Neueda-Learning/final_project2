package com.portfoliomanager.api;

import com.portfoliomanager.api.ApiModels.ErrorDetail;
import com.portfoliomanager.api.ApiModels.ErrorResponse;
import com.portfoliomanager.service.ConflictException;
import com.portfoliomanager.service.InvalidDateRangeException;
import com.portfoliomanager.service.MarketDataUnavailableException;
import com.portfoliomanager.service.ResourceNotFoundException;
import com.portfoliomanager.service.ServiceNotReadyException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(
            ResourceNotFoundException exception,
            HttpServletRequest request) {
        String message = exception.getMessage();
        String code = message != null && message.equals(message.toUpperCase())
            ? message
            : "NOT_FOUND";
        return error(code, message, List.of(), request);
    }

    /** Handles business conflicts such as duplicate names or existing trade history. */
    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse conflict(
            ConflictException exception,
            HttpServletRequest request) {
        return error(exception.getMessage(), "Resource conflict", List.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse validation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        var details = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> new ErrorDetail(field.getField(), field.getDefaultMessage()))
                .toList();
        return error("VALIDATION_ERROR", "Request validation failed", details, request);
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse invalidDateRange(
            InvalidDateRangeException exception,
            HttpServletRequest request) {
        return error("INVALID_DATE_RANGE", exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(MarketDataUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse marketDataUnavailable(
            MarketDataUnavailableException exception,
            HttpServletRequest request) {
        return error("MARKET_DATA_UNAVAILABLE", exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse illegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        return error("VALIDATION_ERROR", exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(ServiceNotReadyException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse serviceNotReady(
            ServiceNotReadyException exception,
            HttpServletRequest request) {
        return error("SERVICE_NOT_READY", exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse illegalState(
            IllegalStateException exception,
            HttpServletRequest request) {
        return error("CONFLICT", exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(OptimisticLockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse optimisticLock(
            OptimisticLockException exception,
            HttpServletRequest request) {
        return error("CONCURRENT_MODIFICATION", "Concurrent modification detected", List.of(), request);
    }

    private ErrorResponse error(
            String code,
            String message,
            List<ErrorDetail> details,
            HttpServletRequest request) {
        var requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        return new ErrorResponse(code, message, details, requestId);
    }
}
