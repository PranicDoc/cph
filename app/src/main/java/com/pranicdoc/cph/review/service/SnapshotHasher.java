package com.pranicdoc.cph.review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;

/**
 * Content hash of the case data a PDF was built from: SHA-256 hex of the JSON re-serialised with keys
 * sorted at every level, so formatting and key order never change it. Two PDF jobs built from the
 * same case content hash the same, which lets a submission skip a revision that would repeat the
 * previous one.
 *
 * <p>Fields in {@link #VOLATILE_FIELDS} are left out. The snapshot cph builds today holds no
 * timestamp, "today" or generated id (checked in CphCaseMapper and CaseService.buildAndEnqueuePdfJob),
 * so the set is empty; add a field name here if one is ever introduced.
 */
public final class SnapshotHasher {

    static final Set<String> VOLATILE_FIELDS = Set.of();

    // Deliberately NOT the Spring-managed mapper: the canonical form that is hashed must stay stable
    // whatever the application mapper is configured to do.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SnapshotHasher() {
    }

    /** The hash, or null when there is no data or it is not valid JSON (a null hash never matches). */
    public static String hash(String json) {
        return hash(json, VOLATILE_FIELDS);
    }

    public static String hash(String json, Set<String> volatileFields) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            String canonical = MAPPER.writeValueAsString(canonical(MAPPER.readTree(json), volatileFields));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.io.IOException e) {
            return null;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode canonical(JsonNode node, Set<String> volatileFields) {
        if (node.isObject()) {
            ObjectNode sorted = JsonNodeFactory.instance.objectNode();
            for (String name : new TreeSet<>(iterable(node.fieldNames()))) {
                if (!volatileFields.contains(name)) {
                    sorted.set(name, canonical(node.get(name), volatileFields));
                }
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            node.forEach(child -> out.add(canonical(child, volatileFields)));
            return out;
        }
        return node;
    }

    private static java.util.List<String> iterable(java.util.Iterator<String> it) {
        java.util.List<String> names = new java.util.ArrayList<>();
        it.forEachRemaining(names::add);
        return names;
    }
}
