package com.pranicdoc.cph.common.http;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Outbound HTTP calls to third parties (WhatsApp, ...). Every method funnels into
 * {@link #exchange}: caller headers are applied first, then auth; a body defaults to JSON unless the
 * caller set a Content-Type. Non-2xx responses and I/O failures are thrown as
 * {@link RestClientException} — retry policy is the caller's decision. Timeouts come from the
 * {@code RestTemplate} bean ({@link RestTemplateConfig}).
 */
@Service
public class RestTemplateService {

    private final RestTemplate restTemplate;

    public RestTemplateService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ─── GET ─────────────────────────────────────────────────────────────────

    public <T> T get(String url, Class<T> responseType) {
        return get(url, Map.of(), HttpAuth.none(), responseType);
    }

    public <T> T get(String url, HttpAuth auth, Class<T> responseType) {
        return get(url, Map.of(), auth, responseType);
    }

    public <T> T get(String url, Map<String, String> headers, HttpAuth auth, Class<T> responseType) {
        return exchange(url, HttpMethod.GET, null, headers, auth, responseType).getBody();
    }

    // ─── POST ────────────────────────────────────────────────────────────────

    public <T> T post(String url, Object body, Class<T> responseType) {
        return post(url, body, Map.of(), HttpAuth.none(), responseType);
    }

    public <T> T post(String url, Object body, HttpAuth auth, Class<T> responseType) {
        return post(url, body, Map.of(), auth, responseType);
    }

    public <T> T post(String url, Object body, Map<String, String> headers, HttpAuth auth, Class<T> responseType) {
        return exchange(url, HttpMethod.POST, body, headers, auth, responseType).getBody();
    }

    // ─── PUT ─────────────────────────────────────────────────────────────────

    public <T> T put(String url, Object body, Class<T> responseType) {
        return put(url, body, Map.of(), HttpAuth.none(), responseType);
    }

    public <T> T put(String url, Object body, HttpAuth auth, Class<T> responseType) {
        return put(url, body, Map.of(), auth, responseType);
    }

    public <T> T put(String url, Object body, Map<String, String> headers, HttpAuth auth, Class<T> responseType) {
        return exchange(url, HttpMethod.PUT, body, headers, auth, responseType).getBody();
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────

    public void delete(String url) {
        delete(url, Map.of(), HttpAuth.none());
    }

    public void delete(String url, HttpAuth auth) {
        delete(url, Map.of(), auth);
    }

    public void delete(String url, Map<String, String> headers, HttpAuth auth) {
        exchange(url, HttpMethod.DELETE, null, headers, auth, Void.class);
    }

    // ─── Core ────────────────────────────────────────────────────────────────

    /** Full control: any method, and the whole response (status, headers, body). */
    public <T> ResponseEntity<T> exchange(String url, HttpMethod method, Object body,
                                          Map<String, String> headers, HttpAuth auth, Class<T> responseType) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
        if (headers != null) {
            headers.forEach(httpHeaders::set);
        }
        (auth == null ? HttpAuth.none() : auth).apply(httpHeaders);
        if (body != null && httpHeaders.getContentType() == null) {
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(url, method, new HttpEntity<>(body, httpHeaders), responseType);
    }
}
