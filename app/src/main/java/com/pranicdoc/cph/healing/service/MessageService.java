package com.pranicdoc.cph.healing.service;

import java.util.UUID;

public interface MessageService {
    void publish(String pdfJob, UUID healingCaseId);
}
