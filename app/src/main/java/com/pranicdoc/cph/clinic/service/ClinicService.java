package com.pranicdoc.cph.clinic.service;

import com.pranicdoc.cph.clinic.dto.ClinicCreateRequest;
import com.pranicdoc.cph.clinic.dto.ClinicResponse;
import com.pranicdoc.cph.clinic.model.Clinic;
import com.pranicdoc.cph.clinic.repository.ClinicRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.payments.CreditServiceClient;
import com.pranicdoc.cph.payments.dto.OwnerType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ClinicService {

    private static final Logger log = LoggerFactory.getLogger(ClinicService.class);

    private final ClinicRepository clinicRepository;
    private final CreditServiceClient creditServiceClient;

    @Auditable(action = "CREATE_CLINIC")
    public ClinicResponse create(ClinicCreateRequest request) {
        Clinic clinic = Clinic.builder()
                .code(request.code())
                .name(request.name())
                .phone(request.phone())
                .email(request.email())
                .addressLine1(request.addressLine1())
                .addressLine2(request.addressLine2())
                .city(request.city())
                .state(request.state())
                .zip(request.zip())
                .active(true)
                .build();

        Clinic saved = clinicRepository.save(clinic);
        try {
            creditServiceClient.createAccount(OwnerType.CLINIC, saved.getId());
        } catch (Exception e) {
            log.warn("CREDIT_ACCOUNT_PROVISION_FAILED clinicId={} reason={} (clinic registration completed; manual provisioning may be needed)",
                    saved.getId(), e.getMessage());
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ClinicResponse> getAll() {
        return clinicRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClinicResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public ClinicResponse getByCode(String code) {
        return toResponse(clinicRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", code)));
    }

    @Auditable(action = "UPDATE_CLINIC")
    public ClinicResponse update(UUID id, ClinicCreateRequest request) {
        Clinic clinic = findOrThrow(id);
        clinic.setName(request.name());
        clinic.setPhone(request.phone());
        clinic.setEmail(request.email());
        clinic.setAddressLine1(request.addressLine1());
        clinic.setAddressLine2(request.addressLine2());
        clinic.setCity(request.city());
        clinic.setState(request.state());
        clinic.setZip(request.zip());
        return toResponse(clinicRepository.save(clinic));
    }

    @Auditable(action = "DELETE_CLINIC")
    public void softDelete(UUID id) {
        Clinic clinic = findOrThrow(id);
        clinic.softDelete();
        clinicRepository.save(clinic);
    }

    private Clinic findOrThrow(UUID id) {
        return clinicRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", id));
    }

    private ClinicResponse toResponse(Clinic c) {
        return new ClinicResponse(
                c.getId(), c.getCode(), c.getName(), c.getPhone(), c.getEmail(),
                c.getAddressLine1(), c.getAddressLine2(), c.getCity(), c.getState(), c.getZip(),
                c.isActive(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
