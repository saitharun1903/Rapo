package com.rideflow.security;

import com.rideflow.exception.ApiErrorFactory;
import com.rideflow.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes {@code ApiError} JSON from servlet filters, where {@code @RestControllerAdvice} does not apply. */
@Component
public class ApiErrorResponseWriter {

    private final ApiErrorFactory errors;
    private final JsonMapper jsonMapper;

    public ApiErrorResponseWriter(ApiErrorFactory errors, JsonMapper jsonMapper) {
        this.errors = errors;
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message)
            throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), errors.create(code, message, request.getRequestURI()));
    }
}
