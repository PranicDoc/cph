package com.pranicdoc.cph.common.tracing;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Sends the correlation ID on calls to our own services, so their log lines join this flow's. The
 * receiving service makes its own request ID, as it is a separate request.
 */
public class TraceHeadersInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String correlationId = RequestContext.correlationId();
        if (correlationId != null) {
            request.getHeaders().set(RequestContext.CORRELATION_ID_HEADER, correlationId);
        }
        return execution.execute(request, body);
    }
}
