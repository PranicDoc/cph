package com.pranicdoc.cph.common.exception;

/**
 * Thrown when a requested entity is not found (or soft-deleted).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String entityName, Object id) {
        super(entityName + " not found with id: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
