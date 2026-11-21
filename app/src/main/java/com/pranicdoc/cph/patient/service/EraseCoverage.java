package com.pranicdoc.cph.patient.service;

import com.pranicdoc.cph.auth.service.HealerEraseService;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The explicit list of tables the erase code knows about, so a new table holding patient or healer data
 * is never erased (or forgotten) by accident. {@code EraseCoverageGuardTest} follows the foreign keys in the
 * Flyway migrations (also through intermediate tables) and fails the build when a table that leads back to
 * a healer, patient, case or session is in neither list.
 *
 * <p>{@link #ERASED} is derived from what the code declares it deletes ({@link RecordPurger#PURGED_TABLES},
 * {@link HealerEraseService#HEALER_TABLES}); an integration test seeds every purged table and checks the rows
 * are gone. Adding a table: erase it there and in the code, or add it to {@link #KEPT} with the reason it
 * must survive an erase. {@code _aud} shadow tables follow their parent.
 */
public final class EraseCoverage {

    /** Tables the erase plan deletes from, or whose rows it re-points or hands over. */
    public static final Set<String> ERASED = erased();

    /** Tables that lead back to a healer, patient, case or session and are deliberately left alone, with why (some hold ids without a foreign key). */
    public static final Map<String, String> KEPT = Map.of(
            "consent", "legal evidence of what the patient agreed to; kept on purpose",
            "audit_log", "legal evidence of who did what; kept on purpose",
            "reading_session", "evidence of which consent text the patient read; kept with the consent records",
            "notification_outbox", "delivery log of sent messages; never erased (known gap: holds a phone number)",
            "prana_color_catalog_properties", "child of a shared catalog colour, which is handed to another super admin, not erased",
            "protocol_chakra_color", "child of a shared protocol, which is handed to another super admin, not erased");

    private EraseCoverage() {
    }

    private static Set<String> erased() {
        Set<String> all = new HashSet<>(RecordPurger.PURGED_TABLES);
        all.addAll(HealerEraseService.HEALER_TABLES);
        return Set.copyOf(all);
    }
}
