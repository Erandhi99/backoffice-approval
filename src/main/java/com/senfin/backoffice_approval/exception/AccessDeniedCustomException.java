package com.senfin.backoffice_approval.exception;

/** Thrown for authorization failures that are business-rule based rather than
 * role based (e.g. a client trying to view someone else's request), which
 * Spring Security's role checks can't express on their own. */
public class AccessDeniedCustomException extends RuntimeException {
    public AccessDeniedCustomException(String message) {
        super(message);
    }
}