package com.senfin.backoffice_approval.service;

import com.senfin.backoffice_approval.dto.*;
import com.senfin.backoffice_approval.entity.*;
import com.senfin.backoffice_approval.exception.AccessDeniedCustomException;
import com.senfin.backoffice_approval.exception.InvalidStateException;
import com.senfin.backoffice_approval.exception.ResourceNotFoundException;
import com.senfin.backoffice_approval.repository.ApprovalHistoryRepository;
import com.senfin.backoffice_approval.repository.ClientRequestRepository;
import com.senfin.backoffice_approval.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * All workflow rules live here, in one place, so the state machine can't drift
 * out of sync between controllers. The rules encoded below are:
 *
 *  1. A client creates a request -> status = PENDING_ENTRY, currentStage = ENTRY.
 *  2. A manager may only act on a request that is currently PENDING_<their stage>.
 *  3. Approving advances the request to the next stage, or to APPROVED if this
 *     was the last stage (MANAGER).
 *  4. Rejecting at ANY stage (including ENTRY) immediately sets status = REJECTED,
 *     records which stage rejected it and why, and clears currentStage.
 *  5. Every transition is appended to ApprovalHistory (immutable audit trail).
 *  6. Only the owning client may edit a REJECTED request; editing resets it to
 *     PENDING_ENTRY (it always restarts from the first checkpoint) and clears
 *     the rejection fields.
 */
@Service
@RequiredArgsConstructor
public class ClientRequestService {

    private final ClientRequestRepository requestRepository;
    private final ApprovalHistoryRepository historyRepository;
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

    @Transactional
    @PreAuthorize("hasAnyRole('ENTRY_MANAGER','ASSISTANT_MANAGER','MANAGER')")
    public ClientRequestResponseDto approve(String approverUsername, Long requestId) {
        ClientRequest request = getRequestOrThrow(requestId);
        User approver = getUser(approverUsername);
        ApprovalStage stage = assertApproverMatchesCurrentStage(request, approver);

        ApprovalStage next = stage.next();
        if (next == null) {
            request.setStatus(RequestStatus.APPROVED);
            request.setCurrentStage(null);
        } else {
            request.setStatus(RequestStatus.pendingFor(next));
            request.setCurrentStage(next);
        }
        requestRepository.save(request);

        recordHistory(request, HistoryAction.APPROVED, stage, approver, null);

        return toDto(request);
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
                history
        );
    }
}