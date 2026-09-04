package com.senfin.backoffice_approval.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Permanent record: one row per fund a permanent (approved) client has invested in.
 * A single client can have multiple rows (different funds, or even the same fund
 * from different approved requests). The source_request_id traces back to exactly
 * which approved request created this investment entry.
 */
@Entity
@Table(name = "client_fund_investments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientFundInvestment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** The approved request that created this particular investment entry. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_request_id", nullable = false)
    private ClientRequest sourceRequest;
}
