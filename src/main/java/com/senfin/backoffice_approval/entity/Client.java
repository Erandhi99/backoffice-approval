package com.senfin.backoffice_approval.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.*;

/**
 * The permanent, system-of-record client. One row per User who has had at least
 * one request fully approved through the Manager stage. Instead of being tied to
 * a single source_request (as in the old 1:1 design), this record links to the
 * User and accumulates fund investments across multiple approved requests.
 * <p>
 * A client is created on FIRST final approval; subsequent approvals for the same
 * user add new fund investment rows without re-creating the client record.
 * By rule, every permanent client must have at least one ClientFundInvestment.
 */
@Entity
@Table(name = "clients", uniqueConstraints = {
        @UniqueConstraint(name = "uk_clients_nic", columnNames = "nic")
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

    /** The user account this permanent client belongs to. One user = one client record. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 20)
    private String nic;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ClientFundInvestment> fundInvestments = new ArrayList<>();

    @Builder.Default
    @Column(name = "approved_at", nullable = false, updatable = false)
    private Instant approvedAt = Instant.now();
}