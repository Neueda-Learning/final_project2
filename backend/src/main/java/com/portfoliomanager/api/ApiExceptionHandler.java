package com.portfoliomanager.api;

import com.portfoliomanager.api.ApiModels.ErrorDetail;
import com.portfoliomanager.api.ApiModels.ErrorResponse;
import com.portfoliomanager.service.ConflictException;
import com.portfoliomanager.service.ResourceNotFoundException;
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
        return error("NOT_FOUND", exception.getMessage(), List.of(), request);
    }

    /** 409 Conflict：业务冲突（名称重复、有交易历史等），code 来自异常 message */
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
        return error("VALIDATION_ERROR", "请求参数校验失败", details, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse illegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        return error("VALIDATION_ERROR", exception.getMessage(), List.of(), request);
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
