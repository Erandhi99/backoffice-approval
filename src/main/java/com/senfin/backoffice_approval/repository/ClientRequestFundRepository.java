package com.senfin.backoffice_approval.repository;

import com.senfin.backoffice_approval.entity.ClientRequestFund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientRequestFundRepository extends JpaRepository<ClientRequestFund, Long> {
    List<ClientRequestFund> findByRequestId(Long requestId);
    void deleteByRequestId(Long requestId);
}
