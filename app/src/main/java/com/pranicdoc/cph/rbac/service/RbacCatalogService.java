package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.rbac.dto.RbacCatalogResourceResponse;
import com.pranicdoc.cph.rbac.dto.RbacCatalogResourceResponse.VerbDto;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * RbacCatalogService -- derives the RBAC grant catalog (38-CONTEXT D-03)
 * purely from the static {@link Resource} / {@link Operation} enums. It has no
 * dependencies and reads no database: {@link Resource#getSupportedOperations()}
 * IS the runtime allow-list now that V33 superseded the
 * {@code resource_permission} table (38-CONTEXT PD-38 correction note).
 * <p>
 * The resource -> label and verb -> label maps live here on purpose: labels
 * are backend-owned per D-03, so {@link Resource} / {@link Operation} stay
 * pure allow-list data with no display concerns and the frontend never has to
 * hardcode a label or render a raw enum token.
 * <p>
 * {@link #catalog()} sorts verbs by enum name and resource entries by label
 * for a stable, testable ordering (mirroring
 * {@code RbacRoleService.toResponse}'s {@code Comparator.comparing(Enum::name)}
 * idiom), and filters out any resource with zero grantable verbs -- nothing is
 * excluded today, but the filter future-proofs D-03's "grantable only" rule.
 */
@Service
public class RbacCatalogService {

    private static final Map<Resource, String> RESOURCE_LABELS = Map.ofEntries(
            Map.entry(Resource.PATIENT, "Patients"),
            Map.entry(Resource.SESSION, "Sessions"),
            Map.entry(Resource.CASE, "Cases"),
            Map.entry(Resource.PDF, "PDF Documents"),
            Map.entry(Resource.CREDIT_ACCOUNTS, "Credit Accounts"),
            Map.entry(Resource.CREDIT_ACCOUNT_TRANSACTIONS, "Credit Account Transactions"),
            Map.entry(Resource.GLOBAL_CONFIG, "Global Configuration"),
            Map.entry(Resource.PACKAGES, "Packages"),
            Map.entry(Resource.MASTER_DATA_COLORS, "Master Data — Colors"),
            Map.entry(Resource.MASTER_DATA_PROTOCOLS, "Master Data — Protocols"),
            Map.entry(Resource.MASTER_DATA_CHAKRAS, "Master Data — Chakras"),
            Map.entry(Resource.HEALER_REG, "Healer Registration"),
            Map.entry(Resource.LEGAL_DOCUMENTS, "Legal Documents"),
            Map.entry(Resource.ROLES, "Roles"),
            Map.entry(Resource.ACCOUNTS, "Healer Accounts")
    );

    private static final Map<Operation, String> VERB_LABELS = Map.of(
            Operation.READ, "Read",
            Operation.CREATE, "Create",
            Operation.UPDATE, "Update",
            Operation.DELETE, "Delete",
            Operation.EXPORT, "Export",
            Operation.SHARE, "Share",
            Operation.WRITE, "Write",
            Operation.ADMIN, "Administer"
    );

    /**
     * One-line, resource-agnostic descriptions the edit grid renders under each
     * verb label (38 mockup Screens 6/9). Backend-owned per D-03 so the
     * frontend never hardcodes copy.
     */
    private static final Map<Operation, String> VERB_DESCRIPTIONS = Map.of(
            Operation.READ, "View records",
            Operation.CREATE, "Add new records",
            Operation.UPDATE, "Edit existing records",
            Operation.DELETE, "Remove records",
            Operation.EXPORT, "Export records to a file",
            Operation.SHARE, "Share records externally",
            Operation.WRITE, "Create and edit records",
            Operation.ADMIN, "Full administrative control"
    );

    /**
     * @return one entry per grantable {@link Resource}, each with its
     * human-readable label and its allow-listed verbs (verbs sorted by enum
     * name, entries sorted by label); resources with no grantable verb are
     * omitted.
     */
    public List<RbacCatalogResourceResponse> catalog() {
        return Arrays.stream(Resource.values())
                .map(RbacCatalogService::toCatalogEntry)
                .filter(entry -> !entry.verbs().isEmpty())
                .sorted(Comparator.comparing(RbacCatalogResourceResponse::label))
                .toList();
    }

    private static RbacCatalogResourceResponse toCatalogEntry(Resource resource) {
        List<VerbDto> verbs = resource.getSupportedOperations().stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(operation -> new VerbDto(
                        operation.name(),
                        verbLabel(operation),
                        verbDescription(operation),
                        isLocked(resource, operation)))
                .toList();
        return new RbacCatalogResourceResponse(resource.name(), resourceLabel(resource), verbs);
    }

    /**
     * A structurally required verb the edit grid shows always-on and
     * non-removable. Today that is only {@code ROLES:UPDATE} — the manage-roles
     * capability (37-CONTEXT D-06). Removing it is what the server-side
     * {@link com.pranicdoc.cph.rbac.RoleManagerGuard self-lockout guard}
     * actually blocks; {@code locked} just surfaces that in the UI ahead of the
     * attempt.
     */
    static boolean isLocked(Resource resource, Operation operation) {
        return resource == Resource.ROLES && operation == Operation.UPDATE;
    }

    /** Package-private for the label-completeness gate in {@code RbacCatalogServiceTest}. */
    static String resourceLabel(Resource resource) {
        return RESOURCE_LABELS.get(resource);
    }

    /** Package-private for the label-completeness gate in {@code RbacCatalogServiceTest}. */
    static String verbLabel(Operation operation) {
        return VERB_LABELS.get(operation);
    }

    /** Package-private for the description-completeness gate in {@code RbacCatalogServiceTest}. */
    static String verbDescription(Operation operation) {
        return VERB_DESCRIPTIONS.get(operation);
    }
}
