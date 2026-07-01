package com.pranicdoc.cph.auth.service.impl;

import com.pranicdoc.cph.auth.dto.HealerResponse;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.service.HealerService;
import com.pranicdoc.cph.clinic.model.Clinic;
import com.pranicdoc.cph.clinic.repository.ClinicRepository;
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
    private final ClinicRepository clinicRepository;
    private final SecurityContextUtil securityContextUtil;

    @Override
    public Healer findHealerByEmail(String email) {
        return healerRepository.findByEmail(email).orElseThrow(() ->
                new ResourceNotFoundException("Healer not found with the email %s".formatted(email)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<HealerResponse> getHealersInClinic() {
        UUID clinicId = securityContextUtil.getCurrentClinicId();
        String clinicCode = securityContextUtil.getCurrentClinicCode();
        return healerRepository.findAllByClinicId(clinicId)
                .stream()
                .map(h -> toResponse(h, clinicCode))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HealerResponse> getHealersByClinic(UUID clinicId) {
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", clinicId));
        return healerRepository.findAllByClinicId(clinicId)
                .stream()
                .map(h -> toResponse(h, clinic.getCode()))
                .toList();
    }

    @Override
    @Transactional
    public HealerResponse setActiveStatus(UUID healerId, boolean active) {
        Healer healer = healerRepository.findById(healerId)
                .orElseThrow(() -> new ResourceNotFoundException("Healer", healerId));

        // ADMIN can only modify healers in their own clinic
        boolean isSuperAdmin = "SUPADM".equals(securityContextUtil.getCurrentRole());
        if (!isSuperAdmin && !healer.getClinicId().equals(securityContextUtil.getCurrentClinicId())) {
            throw new AccessDeniedException("Cannot modify healers outside your clinic");
        }

        healer.setActive(active);
        Healer saved = healerRepository.save(healer);

        Clinic clinic = clinicRepository.findById(saved.getClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", saved.getClinicId()));
        return toResponse(saved, clinic.getCode());
    }

    private HealerResponse toResponse(Healer h, String clinicCode) {
        return new HealerResponse(
                h.getId(), h.getFullName(), h.getEmail(), h.getPhone(),
                h.getRole(), h.getHealingLevel(), h.isActive(),
                h.getClinicId(), clinicCode, h.getCreatedAt()
        );
    }
}
