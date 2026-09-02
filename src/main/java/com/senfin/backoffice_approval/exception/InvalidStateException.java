package com.senfin.backoffice_approval.exception;

/** Thrown when an action is attempted that doesn't fit the request's current
 * workflow state, e.g. trying to edit a request that isn't rejected, or a
 * manager acting on a request that isn't at their stage. */
public class InvalidStateException extends RuntimeException {
    public InvalidStateException(String message) {
        super(message);
    }
}