package com.senfin.backoffice_approval.entity;

/**
 * The three sequential approval checkpoints a request must pass through.
 * Order matters: ENTRY -> ASSISTANT_MANAGER -> MANAGER.
 */
public enum ApprovalStage {
    ENTRY(1, Role.ENTRY_MANAGER),
    ASSISTANT_MANAGER(2, Role.ASSISTANT_MANAGER),
    MANAGER(3, Role.MANAGER);

    private final int order;
    private final Role requiredRole;

    ApprovalStage(int order, Role requiredRole) {
        this.order = order;
        this.requiredRole = requiredRole;
    }

    public int getOrder() {
        return order;
    }

    public Role getRequiredRole() {
        return requiredRole;
    }

    /** The stage that follows this one, or null if this is the last stage. */
    public ApprovalStage next() {
        return switch (this) {
            case ENTRY -> ASSISTANT_MANAGER;
            case ASSISTANT_MANAGER -> MANAGER;
            case MANAGER -> null;
        };
    }
}