package com.pranicdoc.cph.healing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@ConditionalOnProperty(
        name = "app.enable.kafka",
        havingValue = "false",
        matchIfMissing = true
)
public class NoOpMessageServiceImpl implements MessageService {
    @Override
    public void publish(String pdfJob, UUID healingCaseId) {
        log.debug("Using NOOP message service");
    }
}
