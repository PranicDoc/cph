package com.pranicdoc.cph.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WhatsappCloudMessageDto(
        @JsonProperty("messaging_product") String messagingProduct,
        @JsonProperty("recipient_type") String recipientType,
        String to,
        String type,
        TextBody text
) {

    public record TextBody(
            @JsonProperty("preview_url") boolean previewUrl,
            String body
    ) {}

    public static WhatsappCloudMessageDto textMessage(String to, String body) {
        return new WhatsappCloudMessageDto("whatsapp", "individual", to, "text", new TextBody(false, body));
    }
}
