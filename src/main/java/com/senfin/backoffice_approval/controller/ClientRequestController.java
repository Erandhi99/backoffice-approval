package com.senfin.backoffice_approval.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senfin.backoffice_approval.dto.ApprovalActionDto;
import com.senfin.backoffice_approval.dto.ClientRequestResponseDto;
import com.senfin.backoffice_approval.dto.CreateClientRequestDto;
import com.senfin.backoffice_approval.entity.Role;
import com.senfin.backoffice_approval.service.ClientRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class ClientRequestController {

    private final ClientRequestService requestService;

    // ---- Client endpoints ----

    @PostMapping
    public ResponseEntity<ClientRequestResponseDto> submit(
            Authentication auth, @Valid @RequestBody CreateClientRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(requestService.submit(auth.getName(), dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientRequestResponseDto> editAndResubmit(
            Authentication auth, @PathVariable Long id, @Valid @RequestBody CreateClientRequestDto dto) {
        return ResponseEntity.ok(requestService.editAndResubmit(auth.getName(), id, dto));
    }

    @GetMapping("/my")
    public ResponseEntity<List<ClientRequestResponseDto>> myRequests(Authentication auth) {
        return ResponseEntity.ok(requestService.getMyRequests(auth.getName()));
    }

    // ---- Shared read endpoint (client sees own; any manager sees any) ----

    @GetMapping("/{id}")
    public ResponseEntity<ClientRequestResponseDto> getById(Authentication auth, @PathVariable Long id) {
        Role role = primaryRole(auth);
        return ResponseEntity.ok(requestService.getById(auth.getName(), role, id));
    }

    // ---- Manager endpoints ----

    @GetMapping("/queue")
    public ResponseEntity<List<ClientRequestResponseDto>> myQueue(Authentication auth) {
        Role role = primaryRole(auth);
        return ResponseEntity.ok(requestService.getQueueForRole(role));
    }

    /**
     * Approve at the current stage. At ENTRY, the body is REQUIRED -- this is the
     * entry manager entering the client's details into the system. At any other
     * stage, omit the body entirely.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ClientRequestResponseDto> approve(
            Authentication auth, @PathVariable Long id,
            @Valid @RequestBody(required = false) CreateClientRequestDto enteredData) {
        return ResponseEntity.ok(requestService.approve(auth.getName(), id, enteredData));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ClientRequestResponseDto> reject(
            Authentication auth, @PathVariable Long id, @Valid @RequestBody ApprovalActionDto dto) {
        return ResponseEntity.ok(requestService.reject(auth.getName(), id, dto));
    }

    @SuppressWarnings("null")
    private Role primaryRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> Role.valueOf(a.substring(5)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Authenticated user has no role"));
    }
}