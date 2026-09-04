package com.senfin.backoffice_approval.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senfin.backoffice_approval.dto.ClientResponseDto;
import com.senfin.backoffice_approval.service.ClientRequestService;

import lombok.RequiredArgsConstructor;

/**
 * Read-only access to the PERMANENT client table -- i.e. clients that have
 * passed final (MANAGER) approval. This is deliberately separate from
 * /api/requests, which covers in-flight and rejected workflow records too;
 * anything returned from here is, by construction, fully saved and official.
 */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientRequestService requestService;

    @GetMapping
    public List<ClientResponseDto> getAll() {
        return requestService.getAllPermanentClients();
    }

    @GetMapping("/{id}")
    public ClientResponseDto getById(@PathVariable Long id) {
        return requestService.getPermanentClientById(id);
    }
}