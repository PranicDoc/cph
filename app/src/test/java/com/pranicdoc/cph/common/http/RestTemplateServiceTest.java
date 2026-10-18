package com.pranicdoc.cph.common.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestTemplateServiceTest {

    private static final String URL = "https://api.example.com/things";

    private MockRestServiceServer server;
    private RestTemplateService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new RestTemplateService(restTemplate);
    }

    record Thing(String name) {
    }

    @Test
    void get_withoutAuthOrHeaders() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("{\"name\":\"a\"}", MediaType.APPLICATION_JSON));

        assertThat(service.get(URL, Thing.class)).isEqualTo(new Thing("a"));
        server.verify();
    }

    @Test
    void get_withBearerAuth() {
        server.expect(requestTo(URL)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok"))
                .andRespond(withSuccess("{\"name\":\"a\"}", MediaType.APPLICATION_JSON));

        service.get(URL, HttpAuth.bearer("tok"), Thing.class);
        server.verify();
    }

    @Test
    void get_withHeadersAndApiKeyAuth() {
        server.expect(requestTo(URL))
                .andExpect(header("X-Trace", "t-1"))
                .andExpect(header("X-Api-Key", "k"))
                .andRespond(withSuccess("{\"name\":\"a\"}", MediaType.APPLICATION_JSON));

        service.get(URL, Map.of("X-Trace", "t-1"), HttpAuth.apiKey("X-Api-Key", "k"), Thing.class);
        server.verify();
    }

    @Test
    void post_sendsJsonBodyWithDefaultContentType() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("{\"name\":\"new\"}"))
                .andRespond(withSuccess("{\"name\":\"new\"}", MediaType.APPLICATION_JSON));

        assertThat(service.post(URL, new Thing("new"), Thing.class)).isEqualTo(new Thing("new"));
        server.verify();
    }

    @Test
    void post_withBasicAuth() {
        // "user:pass" base64
        server.expect(requestTo(URL)).andExpect(header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andRespond(withSuccess());

        service.post(URL, new Thing("x"), HttpAuth.basic("user", "pass"), Void.class);
        server.verify();
    }

    @Test
    void post_callerContentTypeWins() {
        server.expect(requestTo(URL)).andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE))
                .andRespond(withSuccess());

        service.post(URL, "raw", Map.of(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE), HttpAuth.none(), Void.class);
        server.verify();
    }

    @Test
    void put_andDelete() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.PUT)).andRespond(withSuccess());
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok")).andRespond(withSuccess());

        service.put(URL, new Thing("x"), Void.class);
        service.delete(URL, HttpAuth.bearer("tok"));
        server.verify();
    }

    @Test
    void exchange_returnsStatusAndHeaders() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.ACCEPTED)
                .header("X-Request-Id", "r-1").contentType(MediaType.APPLICATION_JSON).body("{\"name\":\"a\"}"));

        var response = service.exchange(URL, HttpMethod.GET, null, Map.of(), HttpAuth.none(), Thing.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("r-1");
    }

    @Test
    void errorsPropagateToTheCaller() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> service.get(URL, Thing.class)).isInstanceOf(HttpServerErrorException.class);
    }
}
