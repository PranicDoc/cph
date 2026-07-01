package com.pranicdoc.cph.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsappWebhookPayload(
        String object,
        List<Entry> entry
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            String id,
            List<Change> changes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Change(
            Value value,
            String field
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Value(
            @JsonProperty("messaging_product") String messagingProduct,
            Metadata metadata,
            List<IncomingMessage> messages,
            List<StatusUpdate> statuses
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
            @JsonProperty("display_phone_number") String displayPhoneNumber,
            @JsonProperty("phone_number_id") String phoneNumberId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncomingMessage(
            String from,
            String id,
            String timestamp,
            String type,
            TextBody text
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record TextBody(String body) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusUpdate(
            String id,
            String status,
            String timestamp,
            @JsonProperty("recipient_id") String recipientId
    ) {}
}
