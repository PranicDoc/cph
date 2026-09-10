package com.pranicdoc.cph.rbac.model;

/** Permission — a validated (resource, verb) pair. Construction fails fast on an un-allow-listed combo. */
public record Permission(Resource resource, Operation operation) {
    public Permission {
        if (!resource.supports(operation)) {
            throw new IllegalArgumentException(
                    "Operation '%s' is not supported for resource '%s'"
                            .formatted(operation, resource)
            );
        }
    }
}
