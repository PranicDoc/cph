package com.pranicdoc.cph.healing.service;

import com.pranicdoc.cph.healing.model.HealingSession;

public interface NotificationService {
    void sendFinalFeedbackNotification(HealingSession healingSession);
    void sendFeedbackNotification(HealingSession healingSession, boolean isFinal);
}
