package com.senfin.backoffice_approval.repository;

import com.senfin.backoffice_approval.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    boolean existsByNic(String nic);
    Optional<Client> findByUserId(Long userId);
    Optional<Client> findByUserUsername(String username);
}