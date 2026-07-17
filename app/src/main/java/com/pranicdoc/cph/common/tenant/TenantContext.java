package com.pranicdoc.cph.common.tenant;

import java.util.UUID;

public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    public static void set(UUID healerId) {
        CURRENT.set(healerId);
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}