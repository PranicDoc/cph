package com.pranicdoc.cph.healing.service;

import com.pranicdoc.cph.healing.dto.kafka.PdfJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "app.enable.kafka",
        havingValue = "true"
)
public class KafkaMessageServiceImpl implements MessageService {

    private final KafkaTemplate<String, PdfJob> kafkaTemplate;

    @Value("${app.kafka.topics.pdf:pdf-jobs}")
    private String topic;

    @Override
    public void publish(String jobId, UUID healingCaseId) {
        Message<String> message = MessageBuilder
                .withPayload(jobId)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader(KafkaHeaders.KEY, healingCaseId.toString())
                .build();

        kafkaTemplate.send(message)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.error("Kafka send failed for jobId={}",
                                jobId, exception);
                    } else {
                        log.info("Job sent: {} partition: {}",
                                jobId,
                                result.getRecordMetadata().partition());
                    }
                });
    }
}