package com.senfin.backoffice_approval.repository;

import com.senfin.backoffice_approval.entity.ClientRequest;
import com.senfin.backoffice_approval.entity.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientRequestRepository extends JpaRepository<ClientRequest, Long> {

    List<ClientRequest> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<ClientRequest> findByStatusOrderByCreatedAtAsc(RequestStatus status);
}