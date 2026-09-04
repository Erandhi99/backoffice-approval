package com.senfin.backoffice_approval.service;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senfin.backoffice_approval.dto.ApprovalActionDto;
import com.senfin.backoffice_approval.dto.ApprovalHistoryDto;
import com.senfin.backoffice_approval.dto.ClientRequestResponseDto;
import com.senfin.backoffice_approval.dto.ClientResponseDto;
import com.senfin.backoffice_approval.dto.CreateClientRequestDto;
import com.senfin.backoffice_approval.entity.ApprovalHistory;
import com.senfin.backoffice_approval.entity.ApprovalStage;
import com.senfin.backoffice_approval.entity.Client;
import com.senfin.backoffice_approval.entity.ClientRequest;
import com.senfin.backoffice_approval.entity.HistoryAction;
import com.senfin.backoffice_approval.entity.RequestStatus;
import com.senfin.backoffice_approval.entity.Role;
import com.senfin.backoffice_approval.entity.User;
import com.senfin.backoffice_approval.exception.AccessDeniedCustomException;
import com.senfin.backoffice_approval.exception.InvalidStateException;
import com.senfin.backoffice_approval.exception.ResourceNotFoundException;
import com.senfin.backoffice_approval.repository.ApprovalHistoryRepository;
import com.senfin.backoffice_approval.repository.ClientRepository;
import com.senfin.backoffice_approval.repository.ClientRequestRepository;
import com.senfin.backoffice_approval.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * All workflow rules live here, in one place, so the state machine can't drift
 * out of sync between controllers. The rules encoded below are:
 *
 *  1. A client creates a request -> status = PENDING_ENTRY, currentStage = ENTRY.
 *  2. A manager may only act on a request that is currently PENDING_<their stage>.
 *  3. Approving advances the request to the next stage, or to APPROVED if this
 *     was the last stage (MANAGER).
 *  4. The ENTRY_MANAGER's approval is special: it IS the act of entering the
 *     client's details into the system, so it requires a data payload. The other
 *     two approval stages are pure sign-offs and take no body.
 *  5. Rejecting at ANY stage (including ENTRY) immediately sets status = REJECTED,
 *     records which stage rejected it and why, and clears currentStage. No data
 *     entry is required or expected to reject.
 *  6. The client_requests row is a WORKING/STAGING record only. A client is not
 *     permanently saved in the system until the final (MANAGER) approval, at
 *     which point -- and only then -- a row is written to the separate `clients`
 *     table. Nothing else ever writes to that table.
 *  7. Every transition is appended to ApprovalHistory (immutable audit trail).
 *  8. Only the owning client may edit a REJECTED request; editing resets it to
 *     PENDING_ENTRY (it always restarts from the first checkpoint) and clears
 *     the rejection fields.
 */
@Service
@RequiredArgsConstructor
public class ClientRequestService {

    private final ClientRequestRepository requestRepository;
    private final ApprovalHistoryRepository historyRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;

    @Transactional
    @PreAuthorize("hasRole('CLIENT')")
    public ClientRequestResponseDto submit(String clientUsername, CreateClientRequestDto dto) {
        User client = getUser(clientUsername);

        ClientRequest request = ClientRequest.builder()
                .client(client)
                .name(dto.name())
                .nic(dto.nic())
                .address(dto.address())
                .dateOfBirth(dto.dateOfBirth())
                .status(RequestStatus.PENDING_ENTRY)
                .currentStage(ApprovalStage.ENTRY)
                .build();
        requestRepository.save(request);

        recordHistory(request, HistoryAction.SUBMITTED, null, client, "Initial submission");

        return toDto(request);
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENT')")
    public ClientRequestResponseDto editAndResubmit(String clientUsername, Long requestId, CreateClientRequestDto dto) {
        ClientRequest request = getRequestOrThrow(requestId);
        User client = getUser(clientUsername);

        if (!request.getClient().getId().equals(client.getId())) {
            throw new AccessDeniedCustomException("You may only edit your own requests");
        }
        if (request.getStatus() != RequestStatus.REJECTED) {
            throw new InvalidStateException("Only a rejected request can be edited. Current status: " + request.getStatus());
        }

        request.setName(dto.name());
        request.setNic(dto.nic());
        request.setAddress(dto.address());
        request.setDateOfBirth(dto.dateOfBirth());
        request.setStatus(RequestStatus.PENDING_ENTRY);
        request.setCurrentStage(ApprovalStage.ENTRY);
        request.setRejectionStage(null);
        request.setRejectionComment(null);
        requestRepository.save(request);

        recordHistory(request, HistoryAction.RESUBMITTED, null, client, "Client edited and resubmitted after rejection");

        return toDto(request);
    }

    /** Approve at ASSISTANT_MANAGER or MANAGER stage -- no data entry, pure sign-off. */
    @Transactional
    @PreAuthorize("hasAnyRole('ENTRY_MANAGER','ASSISTANT_MANAGER','MANAGER')")
    public ClientRequestResponseDto approve(String approverUsername, Long requestId) {
        return approve(approverUsername, requestId, null);
    }

    /**
     * Approve a request at its current stage. At ENTRY specifically, this is where
     * the client's details actually get entered into the system -- {@code enteredData}
     * is required there and overwrites whatever the client originally submitted.
     * At any other stage a body is neither required nor accepted, since those stages
     * only sign off on data that's already been entered.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ENTRY_MANAGER','ASSISTANT_MANAGER','MANAGER')")
    public ClientRequestResponseDto approve(String approverUsername, Long requestId, CreateClientRequestDto enteredData) {
        ClientRequest request = getRequestOrThrow(requestId);
        User approver = getUser(approverUsername);
        ApprovalStage stage = assertApproverMatchesCurrentStage(request, approver);

        String historyComment = null;
        if (stage == ApprovalStage.ENTRY) {
            if (enteredData == null) {
                throw new IllegalArgumentException(
                        "The entry manager must enter the client's details (name, nic, address, dateOfBirth) to approve.");
            }
            request.setName(enteredData.name());
            request.setNic(enteredData.nic());
            request.setAddress(enteredData.address());
            request.setDateOfBirth(enteredData.dateOfBirth());
            historyComment = "Details entered and approved by entry manager";
        } else if (enteredData != null) {
            throw new IllegalArgumentException(
                    "Only the entry manager enters client details; no body is expected at the " + stage + " stage.");
        }

        ApprovalStage next = stage.next();
        if (next == null) {
            // This was the MANAGER (final) stage -- the request is now fully approved,
            // which is the ONLY moment the client becomes permanently saved.
            request.setStatus(RequestStatus.APPROVED);
            request.setCurrentStage(null);
            requestRepository.save(request);
            promoteToPermanentClient(request);
        } else {
            request.setStatus(RequestStatus.pendingFor(next));
            request.setCurrentStage(next);
            requestRepository.save(request);
        }

        recordHistory(request, HistoryAction.APPROVED, stage, approver, historyComment);

        return toDto(request);
    }

    /** Writes the one-and-only permanent record for this client. Guarded against
     * double-creation (defensive; the workflow shouldn't allow re-approving an
     * already-APPROVED request) and against NIC collisions across separate clients. */
    private void promoteToPermanentClient(ClientRequest request) {
        if (clientRepository.findBySourceRequestId(request.getId()).isPresent()) {
            return; // already promoted -- nothing to do
        }
        if (clientRepository.existsByNic(request.getNic())) {
            throw new InvalidStateException(
                    "A client with NIC " + request.getNic() + " has already been approved under a different request.");
        }
        clientRepository.save(Client.builder()
                .name(request.getName())
                .nic(request.getNic())
                .address(request.getAddress())
                .dateOfBirth(request.getDateOfBirth())
                .sourceRequest(request)
                .build());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ENTRY_MANAGER','ASSISTANT_MANAGER','MANAGER')")
    public ClientRequestResponseDto reject(String approverUsername, Long requestId, ApprovalActionDto dto) {
        ClientRequest request = getRequestOrThrow(requestId);
        User approver = getUser(approverUsername);
        ApprovalStage stage = assertApproverMatchesCurrentStage(request, approver);

        request.setStatus(RequestStatus.REJECTED);
        request.setRejectionStage(stage);
        request.setRejectionComment(dto.comment());
        request.setCurrentStage(null);
        requestRepository.save(request);

        recordHistory(request, HistoryAction.REJECTED, stage, approver, dto.comment());

        return toDto(request);
    }

    @Transactional(readOnly = true)
    public List<ClientRequestResponseDto> getMyRequests(String clientUsername) {
        User client = getUser(clientUsername);
        return requestRepository.findByClientIdOrderByCreatedAtDesc(client.getId())
                .stream().map(this::toDto).toList();
    }

    /** Returns the queue of requests waiting at a given manager's stage. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ENTRY_MANAGER','ASSISTANT_MANAGER','MANAGER')")
    public List<ClientRequestResponseDto> getQueueForRole(Role role) {
        ApprovalStage stage = stageForRole(role);
        return requestRepository.findByStatusOrderByCreatedAtAsc(RequestStatus.pendingFor(stage))
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ClientRequestResponseDto getById(String requesterUsername, Role requesterRole, Long requestId) {
        ClientRequest request = getRequestOrThrow(requestId);
        // Clients may only view their own case; any manager role may view any case
        // (needed since a manager might need context on cases already past their stage).
        if (requesterRole == Role.CLIENT && !request.getClient().getUsername().equals(requesterUsername)) {
            throw new AccessDeniedCustomException("You may only view your own requests");
        }
        return toDto(request);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ENTRY_MANAGER','ASSISTANT_MANAGER','MANAGER')")
    public List<ClientResponseDto> getAllPermanentClients() {
        return clientRepository.findAll().stream().map(this::toClientDto).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ENTRY_MANAGER','ASSISTANT_MANAGER','MANAGER')")
    public ClientResponseDto getPermanentClientById(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + id));
        return toClientDto(client);
    }

    // ---- helpers ----

    private ApprovalStage assertApproverMatchesCurrentStage(ClientRequest request, User approver) {
        if (request.getCurrentStage() == null) {
            throw new InvalidStateException("This request has already reached a final state: " + request.getStatus());
        }
        ApprovalStage stage = request.getCurrentStage();
        if (stage.getRequiredRole() != approver.getRole()) {
            throw new AccessDeniedCustomException(
                    "This request is awaiting " + stage.getRequiredRole() + ", not " + approver.getRole());
        }
        return stage;
    }

    private ApprovalStage stageForRole(Role role) {
        return switch (role) {
            case ENTRY_MANAGER -> ApprovalStage.ENTRY;
            case ASSISTANT_MANAGER -> ApprovalStage.ASSISTANT_MANAGER;
            case MANAGER -> ApprovalStage.MANAGER;
            case CLIENT -> throw new IllegalArgumentException("Clients don't have an approval queue");
        };
    }

    private void recordHistory(ClientRequest request, HistoryAction action, ApprovalStage stage, User performedBy, String comment) {
        historyRepository.save(ApprovalHistory.builder()
                .request(request)
                .action(action)
                .stage(stage)
                .performedBy(performedBy)
                .comment(comment)
                .build());
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private ClientRequest getRequestOrThrow(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + id));
    }

    @SuppressWarnings("null")
    private ClientRequestResponseDto toDto(ClientRequest r) {
        List<ApprovalHistoryDto> history = historyRepository.findByRequestIdOrderByTimestampAsc(r.getId())
                .stream()
                .map(h -> new ApprovalHistoryDto(
                        h.getAction(),
                        h.getStage(),
                        h.getPerformedBy().getUsername(),
                        h.getPerformedBy().getRole().name(),
                        h.getComment(),
                        h.getTimestamp()))
                .toList();

        return new ClientRequestResponseDto(
                r.getId(),
                r.getClient().getUsername(),
                r.getName(),
                r.getNic(),
                r.getAddress(),
                r.getDateOfBirth(),
                r.getStatus(),
                r.getCurrentStage(),
                r.getRejectionStage(),
                r.getRejectionComment(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                history,
                clientRepository.findBySourceRequestId(r.getId()).map(Client::getId).orElse(null)
        );
    }

    private ClientResponseDto toClientDto(Client c) {
        return new ClientResponseDto(
                c.getId(),
                c.getName(),
                c.getNic(),
                c.getAddress(),
                c.getDateOfBirth(),
                c.getSourceRequest().getId(),
                c.getApprovedAt()
        );
    }
}