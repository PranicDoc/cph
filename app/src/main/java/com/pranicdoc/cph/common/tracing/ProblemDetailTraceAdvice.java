package com.pranicdoc.cph.common.tracing;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Every error response passes here, so one place logs them all and gives the caller an ID to quote.
 * Only the error's type goes in the log, not its detail: detail text can repeat what the user typed.
 */
@Slf4j
@ControllerAdvice
public class ProblemDetailTraceAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ProblemDetail problem) {
            String requestId = RequestContext.requestId();
            if (requestId != null) {
                problem.setProperty("requestId", requestId);
            }
            logFailure(problem, request);
        }
        return body;
    }

    private static void logFailure(ProblemDetail problem, ServerHttpRequest request) {
        HttpServletRequest servlet = request instanceof ServletServerHttpRequest s ? s.getServletRequest() : null;
        String method = servlet != null ? servlet.getMethod() : "";
        String path = servlet != null ? servlet.getRequestURI() : "";
        if (problem.getStatus() >= 500) {
            log.error("Request failed: {} {} status={} type={}", method, path, problem.getStatus(), problem.getType());
        } else {
            log.warn("Request rejected: {} {} status={} type={}", method, path, problem.getStatus(), problem.getType());
        }
    }
}
