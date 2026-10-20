package com.pranicdoc.cph.notification.service;

import com.pranicdoc.cph.common.http.HttpAuth;
import com.pranicdoc.cph.common.http.RestTemplateService;
import com.pranicdoc.cph.notification.dto.WhatsappCloudMessageDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The WhatsApp Cloud API call, and nothing else. Throws on any failure; callers decide on retries. */
@Component
public class WhatsappSender {

    private final RestTemplateService http;

    @Value("${app.whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${app.whatsapp.access-token}")
    private String accessToken;

    public WhatsappSender(RestTemplateService http) {
        this.http = http;
    }

    public void send(String phoneNumber, String text) {
        String url = "https://graph.facebook.com/v18.0/" + phoneNumberId + "/messages";
        http.post(url, WhatsappCloudMessageDto.textMessage(phoneNumber, text), HttpAuth.bearer(accessToken), String.class);
    }
}
