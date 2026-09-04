package com.senfin.backoffice_approval.repository;

import com.senfin.backoffice_approval.entity.ClientFundInvestment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientFundInvestmentRepository extends JpaRepository<ClientFundInvestment, Long> {
    List<ClientFundInvestment> findByClientId(Long clientId);
    List<ClientFundInvestment> findByFundId(Long fundId);
}
