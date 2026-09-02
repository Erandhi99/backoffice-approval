package com.senfin.backoffice_approval.entity;

/**
 * Lifecycle status of a ClientRequest.
 * PENDING_* mirrors the ApprovalStage currently holding the request.
 */
public enum RequestStatus {
    PENDING_ENTRY,
    PENDING_ASSISTANT_MANAGER,
    PENDING_MANAGER,
    APPROVED,
    REJECTED;

    public static RequestStatus pendingFor(ApprovalStage stage) {
        return switch (stage) {
            case ENTRY -> PENDING_ENTRY;
            case ASSISTANT_MANAGER -> PENDING_ASSISTANT_MANAGER;
            case MANAGER -> PENDING_MANAGER;
        };
    }
}