package com.senfin.backoffice_approval.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents one "client onboarding / fund investment approval" case.
 * The client's personal details come from the User entity (auto-retrieved);
 * this record tracks which fund(s) they want to invest in and the requested amount(s).
 * A single row is mutated as it moves through the workflow (status/stage change in place);
 * the full history of what happened is kept in ApprovalHistory, not here.
 */
@Entity
@Table(name = "client_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The client this request/data belongs to. Immutable after creation. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequestStatus status;

    /**
     * Which stage the request is currently sitting at, awaiting action.
     * Null once the request reaches a terminal state (APPROVED/REJECTED).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", length = 30)
    private ApprovalStage currentStage;

    /** Populated only when status == REJECTED. Tells the client exactly where/why it failed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_stage", length = 30)
    private ApprovalStage rejectionStage;

    @Column(name = "rejection_comment", length = 1000)
    private String rejectionComment;

    /** Optimistic locking: protects against two managers actioning the same request at once. */
    @Version
    private Long version;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ClientRequestFund> fundInvestments = new ArrayList<>();

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}