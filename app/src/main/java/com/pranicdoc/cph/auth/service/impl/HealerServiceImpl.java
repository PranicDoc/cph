package com.pranicdoc.cph.auth.service.impl;

import com.pranicdoc.cph.auth.dto.HealerResponse;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.service.HealerService;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HealerServiceImpl implements HealerService {

    private final HealerRepository healerRepository;
    private final SecurityContextUtil securityContextUtil;

    @Override
    public Healer findHealerByEmail(String email) {
        return healerRepository.findByEmail(email).orElseThrow(() ->
                new ResourceNotFoundException("Healer not found with the email %s".formatted(email)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<HealerResponse> getHealersByClinic() {
        return healerRepository.findAll()
                .stream()
                // TODO(26-09): HealerResponse loses clinicCode; passing null through the
                // still-clinic-shaped toResponse signature until 26-09's contract cutover.
                .map(h -> toResponse(h, null))
                .toList();
    }

    @Override
    @Transactional
    public HealerResponse setActiveStatus(UUID healerId, boolean active) {
        Healer healer = healerRepository.findById(healerId)
                .orElseThrow(() -> new ResourceNotFoundException("Healer", healerId));

        // SUPADM can modify any healer; an ordinary healer can only modify themselves.
        boolean isSuperAdmin = "SUPADM".equals(securityContextUtil.getCurrentRole());
        if (!isSuperAdmin && !healerId.equals(securityContextUtil.getCurrentHealerId())) {
            throw new AccessDeniedException("Cannot modify another healer's account");
        }

        healer.setActive(active);
        Healer saved = healerRepository.save(healer);

        // TODO(26-09): HealerResponse loses clinicCode; passing null through the
        // still-clinic-shaped toResponse signature until 26-09's contract cutover.
        return toResponse(saved, null);
    }

    private HealerResponse toResponse(Healer h, String clinicCode) {
        return new HealerResponse(
                h.getId(), h.getFullName(), h.getEmail(), h.getPhone(),
                h.getRole(), h.getHealingLevel(), h.isActive(),
                h.getClinicId(), clinicCode, h.getCreatedAt(),
                h.getPasswordHash() != null, h.getProviderUserId() != null
        );
    }
}
