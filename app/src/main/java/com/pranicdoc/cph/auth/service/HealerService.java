package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.auth.model.Healer;

public interface HealerService {
    Healer findHealerByEmail(String email);
}
