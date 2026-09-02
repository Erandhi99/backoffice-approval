package com.senfin.backoffice_approval.entity;

/** What happened to a request at a given point in time -- feeds the audit trail. */
public enum HistoryAction {
    SUBMITTED,
    APPROVED,
    REJECTED,
    RESUBMITTED
}