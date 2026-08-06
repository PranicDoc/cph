package com.pranicdoc.cph.consent.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.consent.dto.ConsentTypeCreateRequest;
import com.pranicdoc.cph.consent.dto.ConsentTypeRenameRequest;
import com.pranicdoc.cph.consent.dto.ConsentTypeResponse;
import com.pranicdoc.cph.consent.model.ConsentType;
import com.pranicdoc.cph.consent.repository.ConsentTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * ConsentTypeService — makes the {@code consent_type} registry writable
 * (Phase 31.1, CONSENT-09/CONSENT-10/CONSENT-11). Every guarantee here is a
 * server-side rule, deliberately not a UI affordance, because the API is
 * directly callable:
 *
 * <ul>
 *   <li>{@code create} derives {@code key} by slugifying {@code label}
 *       (D-05) and hardcodes a safe, non-mandatory/active/non-system default
 *       (D-01/CONSENT-10) — a client cannot mint a mandatory or
 *       system-defined type even by crafting extra JSON fields
 *       (T-31.1-04).</li>
 *   <li>{@code rename} changes only the label — {@code key} is immutable
 *       once created.</li>
 *   <li>{@code setMandatory}/{@code setActive} refuse ADMIN callers on a
 *       system-defined row; only SUPADM may override (D-07,
 *       T-31.1-01), mirroring {@code MasterDataService}'s
 *       system-row guard.</li>
 *   <li>{@code setActive} never touches the inherited row-removal flag —
 *       deactivation is always the dedicated {@code active} column
 *       (D-02/CONSENT-11, T-31.1-11); {@code BaseEntity}'s own removal
 *       mechanism would otherwise hide the row from every query via
 *       {@code @SQLRestriction}, functionally a permanent removal.</li>
 * </ul>
 *
 * No removal capability of any kind exists anywhere in this service —
 * there is no safe caller for one (D-02).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConsentTypeService {

    private final ConsentTypeRepository consentTypeRepository;

    /** D-08's data source — not cached; low-traffic admin read. */
    @Transactional(readOnly = true)
    public List<ConsentTypeResponse> list(boolean includeInactive) {
        List<ConsentType> types = includeInactive
                ? consentTypeRepository.findAllByOrderByIdAsc()
                : consentTypeRepository.findAllByActiveTrueOrderByIdAsc();
        return types.stream().map(this::toResponse).toList();
    }

    /**
     * Slugifies the label into a server-derived key (D-05), rejects a
     * colliding slug (active or inactive) before saving, and hardcodes the
     * safety default: non-mandatory, active, not system-defined
     * (D-01/CONSENT-10). No {@code @CacheEvict} — a brand-new type has no
     * published version, so it cannot yet appear in the cached
     * active-version list.
     */
    @Auditable(action = "CREATE_CONSENT_TYPE")
    public ConsentTypeResponse create(ConsentTypeCreateRequest request) {
        String key = slugify(request.label());

        if (consentTypeRepository.existsByKey(key)) {
            throw new IllegalArgumentException(
                    "A consent form with that name already exists. Please choose a different name.");
        }

        ConsentType type = ConsentType.builder()
                .label(request.label())
                .mandatory(false)
                .active(true)
                .systemDefined(false)
                .build();
        type.setKey(key);

        return toResponse(consentTypeRepository.save(type));
    }

    /**
     * Label-only rename. Deliberately no {@code systemDefined} guard and no
     * {@code isSuperAdmin} parameter — D-07 carves renaming out as
     * cosmetic-only. Never touches {@code key}.
     */
    @Auditable(action = "UPDATE_CONSENT_TYPE")
    public ConsentTypeResponse rename(UUID id, ConsentTypeRenameRequest request) {
        ConsentType type = getById(id);
        type.setLabel(request.label());
        return toResponse(consentTypeRepository.save(type));
    }

    /** D-07: ADMIN may not un-mandate a system-defined type; only SUPADM can. */
    @Auditable(action = "UPDATE_CONSENT_TYPE_MANDATORY")
    public ConsentTypeResponse setMandatory(UUID id, boolean mandatory, boolean isSuperAdmin) {
        ConsentType type = getById(id);
        if (type.isSystemDefined() && !isSuperAdmin) {
            throw new AccessDeniedException("System consent types can only be un-mandated by SUPADM");
        }
        type.setMandatory(mandatory);
        return toResponse(consentTypeRepository.save(type));
    }

    /**
     * D-07: ADMIN may not deactivate a system-defined type; only SUPADM can.
     * Evicts the cached active-version list so a deactivated type's content
     * leaves it immediately (RESEARCH.md Open Question 2) — the cache key
     * must stay byte-identical to {@code ConsentService}'s. Sets the
     * dedicated {@code active} column only; never invokes the inherited
     * removal mechanism (D-02/CONSENT-11, T-31.1-11).
     */
    @Auditable(action = "UPDATE_CONSENT_TYPE_ACTIVE")
    @CacheEvict(value = "policy.active-version", key = "'all'")
    public ConsentTypeResponse setActive(UUID id, boolean active, boolean isSuperAdmin) {
        ConsentType type = getById(id);
        if (type.isSystemDefined() && !isSuperAdmin) {
            throw new AccessDeniedException("System consent types can only be deactivated by SUPADM");
        }
        type.setActive(active);
        return toResponse(consentTypeRepository.save(type));
    }

    /**
     * D-05/Pitfall 4: lowercase, collapse runs of non-alphanumerics to a
     * single hyphen, trim leading/trailing hyphens. A label that slugifies
     * to nothing (or fewer than 2 characters) is rejected before any
     * repository call — a symbol-only label must never reach
     * {@code existsByKey}.
     */
    private static String slugify(String label) {
        String slug = label.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank() || slug.length() < 2) {
            throw new IllegalArgumentException(
                    "Please choose a name that includes at least a couple of letters or numbers.");
        }
        return slug;
    }

    private ConsentType getById(UUID id) {
        return consentTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConsentType", id));
    }

    private ConsentTypeResponse toResponse(ConsentType t) {
        return new ConsentTypeResponse(
                t.getId(),
                t.getKey(),
                t.getLabel(),
                t.isMandatory(),
                t.isActive(),
                t.isSystemDefined(),
                t.getCreatedAt()
        );
    }
}
