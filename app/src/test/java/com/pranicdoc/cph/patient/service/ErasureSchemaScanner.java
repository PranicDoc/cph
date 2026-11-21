package com.pranicdoc.cph.patient.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Flyway scripts as plain text and works out which tables lead back to a healer, patient, case or
 * session through foreign keys, directly or through other tables.
 */
final class ErasureSchemaScanner {

    static final Set<String> ROOTS = Set.of("healer", "patient", "healing_case", "healing_session");

    private static final Pattern STATEMENT_TABLE = Pattern.compile(
            "^\\s*(create\\s+table(?:\\s+if\\s+not\\s+exists)?|alter\\s+table(?:\\s+only)?(?:\\s+if\\s+exists)?|drop\\s+table(?:\\s+if\\s+exists)?)\\s+(?:public\\.)?\"?([a-z_0-9]+)\"?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REFERENCES = Pattern.compile(
            "references\\s+(?:public\\.)?\"?([a-z_0-9]+)\"?\\s*\\(", Pattern.CASE_INSENSITIVE);

    record Schema(Set<String> existingTables, Set<String> ownedTables) {
    }

    private ErasureSchemaScanner() {
    }

    /** @param scripts the SQL texts in migration order */
    static Schema scan(List<String> scripts) {
        Set<String> existing = new TreeSet<>();
        Map<String, Set<String>> edges = new HashMap<>();
        for (String script : scripts) {
            String sql = script.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("--[^\\r\\n]*", " ");
            for (String statement : sql.split(";")) {
                Matcher m = STATEMENT_TABLE.matcher(statement);
                if (!m.find()) {
                    continue;
                }
                String kind = m.group(1).toLowerCase(Locale.ROOT);
                String table = m.group(2).toLowerCase(Locale.ROOT);
                if (kind.startsWith("drop")) {
                    existing.remove(table);
                    edges.remove(table);
                    continue;
                }
                if (kind.startsWith("create")) {
                    existing.add(table);
                }
                Matcher r = REFERENCES.matcher(statement);
                while (r.find()) {
                    edges.computeIfAbsent(table, t -> new TreeSet<>()).add(r.group(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        Set<String> reaching = new TreeSet<>(ROOTS);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Map.Entry<String, Set<String>> e : edges.entrySet()) {
                String table = e.getKey();
                if (!table.endsWith("_aud") && !reaching.contains(table)
                        && e.getValue().stream().anyMatch(reaching::contains)) {
                    reaching.add(table);
                    grew = true;
                }
            }
        }
        reaching.retainAll(existing);
        return new Schema(existing, reaching);
    }
}
