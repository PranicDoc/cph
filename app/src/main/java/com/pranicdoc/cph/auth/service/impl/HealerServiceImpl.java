package com.pranicdoc.cph.auth.service.impl;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.service.HealerService;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealerServiceImpl implements HealerService {
    private final HealerRepository healerRepository;

    @Override
    public Healer findHealerByEmail(String email) {
        return healerRepository.findByEmail(email).orElseThrow(() ->
                new ResourceNotFoundException("Healer not found with the email %s".formatted(email)));
    }
}
