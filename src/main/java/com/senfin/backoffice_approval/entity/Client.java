package com.senfin.backoffice_approval.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The permanent, system-of-record client. Deliberately a SEPARATE table from
 * ClientRequest: a row here is only ever created once, at the moment the final
 * (MANAGER) approval happens. Everything before that -- the client's original
 * submission, the entry manager's entered data, intermediate approvals -- lives
 * only in ClientRequest, which is a workflow/staging record, not the source of truth.
 * This is what lets the rest of the business (or other systems) query "our actual
 * approved clients" without ever seeing in-flight or rejected applications.
 */
@Entity
@Table(name = "clients", uniqueConstraints = {
        @UniqueConstraint(name = "uk_clients_nic", columnNames = "nic"),
        @UniqueConstraint(name = "uk_clients_source_request", columnNames = "source_request_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 20)
    private String nic;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    /** The workflow record this permanent client was created from. One-to-one:
     * a given request can only ever produce one permanent client. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_request_id", nullable = false, unique = true)
    private ClientRequest sourceRequest;

    @Builder.Default
    @Column(name = "approved_at", nullable = false, updatable = false)
    private Instant approvedAt = Instant.now();
}