package com.senfin.backoffice_approval.entity;

/**
 * System roles. Note there are 4 roles even though only 3 (ENTRY_MANAGER,
 * ASSISTANT_MANAGER, MANAGER) participate in the approval chain -- CLIENT is
 * the requester, not an approver.
 */
public enum Role {
    CLIENT,
    ENTRY_MANAGER,
    ASSISTANT_MANAGER,
    MANAGER
}