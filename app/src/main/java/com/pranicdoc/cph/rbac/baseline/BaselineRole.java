package com.pranicdoc.cph.rbac.baseline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The default working role every healer gets on onboarding so the Phase-42
 * capability gates don't lock a freshly-created account out of the core app.
 *
 * <p><b>Definition is data, not code.</b> {@link #NAME}, {@link #DESCRIPTION}
 * and {@link #GRANTS} are loaded from the classpath resource
 * {@value #DEFINITION_RESOURCE} — the single source of truth shared with
 * {@code scripts/backfill_practitioner_role.py} (the pre/post-deploy backfill),
 * which reads the very same file from the repo. Edit the JSON; both the in-app
 * seeder and the backfill script pick the change up, so the two can never
 * drift.
 *
 * <p>Grants cover the healer-facing surface only (patient / case / session /
 * pdf / scan / session-protocol / feedback / credit) plus read-only master
 * data — no admin resources (ROLES, GLOBAL_CONFIG, LEGAL_DOCUMENTS, ACCOUNTS,
 * HEALER_REG, PACKAGES). It is a plain role: not protected by an
 * {@code is_system} flag (deferred), so an operator can still edit or delete
 * it via the Roles UI.
 */
public final class BaselineRole {

    /** Classpath location of the shared role definition. */
    public static final String DEFINITION_RESOURCE = "rbac/baseline-role.json";

    public static final String NAME;
    public static final String DESCRIPTION;

    /** Resource -&gt; granted verbs, parsed from {@value #DEFINITION_RESOURCE}. */
    public static final Map<Resource, Set<Operation>> GRANTS;

    static {
        try (InputStream in = BaselineRole.class.getResourceAsStream("/" + DEFINITION_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Baseline role definition not on classpath: " + DEFINITION_RESOURCE);
            }
            JsonNode root = new ObjectMapper().readTree(in);
            NAME = root.path("name").asText(null);
            DESCRIPTION = root.path("description").asText(null);
            if (NAME == null || NAME.isBlank() || DESCRIPTION == null || DESCRIPTION.isBlank()) {
                throw new IllegalStateException(
                        "Baseline role definition is missing 'name' or 'description'");
            }

            Map<Resource, Set<Operation>> grants = new LinkedHashMap<>();
            JsonNode grantsNode = root.path("grants");
            grantsNode.fieldNames().forEachRemaining(resourceName -> {
                Set<Operation> ops = new LinkedHashSet<>();
                grantsNode.get(resourceName).forEach(op -> {
                    Operation operation = Operation.valueOf(op.asText());
                    Resource resource = Resource.valueOf(resourceName);
                    if (!resource.supports(operation)) {
                        throw new IllegalStateException(
                                "Baseline role grants " + resourceName + ":" + op.asText()
                                        + " but Resource." + resourceName + " does not support it");
                    }
                    ops.add(operation);
                });
                grants.put(Resource.valueOf(resourceName), Set.copyOf(ops));
            });
            if (grants.isEmpty()) {
                throw new IllegalStateException("Baseline role definition has no grants");
            }
            GRANTS = Map.copyOf(grants);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load baseline role definition from classpath:/" + DEFINITION_RESOURCE, e);
        }
    }

    private BaselineRole() {}
}
