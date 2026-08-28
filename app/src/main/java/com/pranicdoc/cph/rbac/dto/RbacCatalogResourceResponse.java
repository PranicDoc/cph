package com.pranicdoc.cph.rbac.dto;

import java.util.List;

/**
 * RBAC grant-catalog entry contract -- one grantable resource plus its
 * allow-listed verbs, each carrying a backend-owned human-readable label. No
 * validation annotations: this is a response record, never bound from a
 * request body.
 * <p>
 * The catalog is derived at runtime from the static
 * {@link com.pranicdoc.cph.rbac.model.Resource} /
 * {@link com.pranicdoc.cph.rbac.model.Operation} enums (38-CONTEXT D-02/D-03);
 * there is no {@code resource_permission} table (V33 superseded it). The
 * frontend consumes this so the create/edit permission grid and the
 * resource-picker never hardcode the allow-list and never render a raw enum
 * token like {@code LEGAL_DOCUMENTS}.
 */
public record RbacCatalogResourceResponse(String resource, String label, List<VerbDto> verbs) {

    /**
     * A single grantable verb: the raw
     * {@link com.pranicdoc.cph.rbac.model.Operation} name, its human-readable
     * label, a one-line description the edit grid renders under the label, and
     * {@code locked} — true for a structurally required verb the UI shows
     * always-on and non-removable (currently only {@code ROLES:UPDATE}, the
     * manage-roles capability; the real self-lockout enforcement stays
     * server-side in {@code RoleManagerGuard}). Response record, never bound
     * from a request body.
     */
    public record VerbDto(String value, String label, String description, boolean locked) {}
}
