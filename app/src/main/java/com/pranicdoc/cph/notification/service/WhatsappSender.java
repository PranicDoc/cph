package com.pranicdoc.cph.notification.service;

import com.pranicdoc.cph.notification.dto.WhatsappCloudMessageDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** The WhatsApp Cloud API call, and nothing else. Throws on any failure; callers decide on retries. */
@Component
public class WhatsappSender {

    private final RestTemplate restTemplate;

    @Value("${app.whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${app.whatsapp.access-token}")
    private String accessToken;

    public WhatsappSender(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void send(String phoneNumber, String text) {
        String url = "https://graph.facebook.com/v18.0/" + phoneNumberId + "/messages";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        WhatsappCloudMessageDto dto = WhatsappCloudMessageDto.textMessage(phoneNumber, text);
        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(dto, headers), String.class);
    }
}
