package com.pranicdoc.cph.common.tracing;

import com.pranicdoc.cph.healing.model.PdfJobEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TracePropagationTest {

    @AfterEach
    void cleanUp() {
        MDC.clear();
    }

    @Test
    void aPoolThreadSeesTheSubmittersIds_andIsCleanAfterwards() throws Exception {
        RequestContext.set("request-1234", "flow-1234");
        Runnable decorated = new MdcTaskDecorator().decorate(() -> { });
        AtomicReference<String> seen = new AtomicReference<>();
        Runnable capturing = new MdcTaskDecorator().decorate(() -> seen.set(RequestContext.correlationId()));
        AtomicReference<String> after = new AtomicReference<>("unset");

        Thread pool = new Thread(() -> {
            decorated.run();
            capturing.run();
            after.set(RequestContext.correlationId());
        });
        pool.start();
        pool.join();

        assertThat(seen).hasValue("flow-1234");
        assertThat(after.get()).isNull();
    }

    @Test
    void callsToOtherServicesCarryTheCorrelationId() throws Exception {
        RequestContext.set("request-1234", "flow-1234");
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST, "http://credit/deduct");

        new TraceHeadersInterceptor().intercept(request, new byte[0], (req, body) -> new MockClientHttpResponse());

        assertThat(request.getHeaders().getFirst(RequestContext.CORRELATION_ID_HEADER)).isEqualTo("flow-1234");
    }

    @Test
    void aCallOffAnyFlowSendsNoHeader() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST, "http://credit/deduct");

        new TraceHeadersInterceptor().intercept(request, new byte[0], (req, body) -> new MockClientHttpResponse());

        assertThat(request.getHeaders().containsKey(RequestContext.CORRELATION_ID_HEADER)).isFalse();
    }

    @Test
    void aNewPdfJobRemembersItsFlow() {
        RequestContext.set("request-1234", "flow-1234");

        PdfJobEntity job = PdfJobEntity.create("job-1", UUID.randomUUID(), "{}");

        assertThat(job.getCorrelationId()).isEqualTo("flow-1234");
    }
}
