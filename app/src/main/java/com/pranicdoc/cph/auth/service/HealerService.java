package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.auth.dto.HealerResponse;
import com.pranicdoc.cph.auth.model.Healer;

import java.util.List;
import java.util.UUID;

public interface HealerService {
    Healer findHealerByEmail(String email);

    List<HealerResponse> getHealersInClinic();

    List<HealerResponse> getHealersByClinic(UUID clinicId);

    HealerResponse setActiveStatus(UUID healerId, boolean active);
}
