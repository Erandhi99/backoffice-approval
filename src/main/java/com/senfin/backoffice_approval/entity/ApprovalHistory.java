package com.senfin.backoffice_approval.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Immutable audit trail entry. One row per meaningful event on a ClientRequest:
 * submission, an approval, a rejection, or a client resubmission after edits.
 */
@Entity
@Table(name = "approval_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ClientRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HistoryAction action;

    /** Null for SUBMITTED / RESUBMITTED events, since those aren't tied to one checkpoint. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ApprovalStage stage;

    /** Who performed this action (the client for SUBMITTED/RESUBMITTED, a manager otherwise). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by", nullable = false)
    private User performedBy;

    @Column(length = 1000)
    private String comment;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant timestamp = Instant.now();
}