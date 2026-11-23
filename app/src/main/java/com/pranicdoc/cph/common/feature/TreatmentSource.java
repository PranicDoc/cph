package com.pranicdoc.cph.common.feature;

/**
 * Where a flag's treatment comes from (Harness FME in production). Returns {@code on}, {@code off}
 * or {@code control} (flag unknown, SDK not ready, or connectivity trouble).
 */
public interface TreatmentSource {

    String treatment(String trafficKey, String flagName);
}
