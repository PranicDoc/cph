package com.pranicdoc.cph.common.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs first on every request: takes the IDs nginx set (or makes new ones where there is no nginx, as
 * on Cloud Run), makes them available to all logging on this thread, returns them to the caller, and
 * logs one summary line when the request ends. The path is logged without its query string, which can
 * hold tokens.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceContextFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().contains("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = RequestContext.validOrNew(request.getHeader(RequestContext.REQUEST_ID_HEADER));
        String inboundCorrelation = request.getHeader(RequestContext.CORRELATION_ID_HEADER);
        String correlationId = inboundCorrelation == null ? requestId : RequestContext.validOrNew(inboundCorrelation);

        RequestContext.set(requestId, correlationId);
        response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId);
        response.setHeader(RequestContext.CORRELATION_ID_HEADER, correlationId);

        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("{} {} status={} durationMs={}", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), (System.nanoTime() - startedAt) / 1_000_000);
            RequestContext.clear();
        }
    }
}
