package com.senfin.backoffice_approval.service;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senfin.backoffice_approval.dto.ApprovalActionDto;
import com.senfin.backoffice_approval.dto.ApprovalHistoryDto;
import com.senfin.backoffice_approval.dto.ClientFundInvestmentDto;
import com.senfin.backoffice_approval.dto.ClientRequestResponseDto;
import com.senfin.backoffice_approval.dto.ClientResponseDto;
import com.senfin.backoffice_approval.dto.CreateClientRequestDto;
import com.senfin.backoffice_approval.dto.FundInvestmentDto;
import com.senfin.backoffice_approval.entity.ApprovalHistory;
import com.senfin.backoffice_approval.entity.ApprovalStage;
import com.senfin.backoffice_approval.entity.Client;
import com.senfin.backoffice_approval.entity.ClientFundInvestment;
import com.senfin.backoffice_approval.entity.ClientRequest;
import com.senfin.backoffice_approval.entity.ClientRequestFund;
import com.senfin.backoffice_approval.entity.Fund;
import com.senfin.backoffice_approval.entity.HistoryAction;
import com.senfin.backoffice_approval.entity.RequestStatus;
import com.senfin.backoffice_approval.entity.Role;
import com.senfin.backoffice_approval.entity.User;
import com.senfin.backoffice_approval.exception.AccessDeniedCustomException;
import com.senfin.backoffice_approval.exception.InvalidStateException;
import com.senfin.backoffice_approval.exception.ResourceNotFoundException;
import com.senfin.backoffice_approval.repository.ApprovalHistoryRepository;
import com.senfin.backoffice_approval.repository.ClientFundInvestmentRepository;
import com.senfin.backoffice_approval.repository.ClientRepository;
import com.senfin.backoffice_approval.repository.ClientRequestFundRepository;
import com.senfin.backoffice_approval.repository.ClientRequestRepository;
import com.senfin.backoffice_approval.repository.FundRepository;
import com.senfin.backoffice_approval.repository.UserRepository;

import lombok.RequiredArgsConstructor;
/**
 * All workflow rules live here, in one place, so the state machine can't drift
 * out of sync between controllers. The rules encoded below are:
 *
 *  1. A client creates a request with fund investment details -> status = PENDING_ENTRY,
 *     currentStage = ENTRY. Personal details are auto-retrieved from the User account.
 *  2. A manager may only act on a request that is currently PENDING_<their stage>.
 *  3. Approving advances the request to the next stage, or to APPROVED if this
 *     was the last stage (MANAGER).
 *  4. The ENTRY_MANAGER's approval is special: it may update the fund investment
 *     details (entering/verifying them into the system).
 *  5. Rejecting at ANY stage (including ENTRY) immediately sets status = REJECTED,
 *     records which stage rejected it and why, and clears currentStage.
 *  6. The client_requests row is a WORKING/STAGING record only. A client is not
 *     permanently saved in the system until the final (MANAGER) approval, at
 *     which point -- and only then -- a row is written/updated in the separate
 *     `clients` table and `client_fund_investments` rows are created.
 *  7. When the same user sends multiple fund requests, the existing permanent
 *     client record is updated with new fund investments (accumulated).
 *  8. Every transition is appended to ApprovalHistory (immutable audit trail).
 *  9. Only the owning client may edit a REJECTED request; editing resets it to
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
    private final FundRepository fundRepository;
    private final ClientRequestFundRepository requestFundRepository;
    private final ClientFundInvestmentRepository fundInvestmentRepository;
// ==================== Client-facing ====================

    @Transactional
    @PreAuthorize("hasRole('CLIENT')")
    public ClientRequestResponseDto submit(String clientUsername, CreateClientRequestDto dto) {
        User client = getUser(clientUsername);

        ClientRequest request = ClientRequest.builder()
                .client(client)
                .status(RequestStatus.PENDING_ENTRY)
                .currentStage(ApprovalStage.ENTRY)
                .build();

        request = requestRepository.save(request);

        // Attach fund investments from the DTO
        attachFundsToRequest(request, dto.fundInvestments());

        recordHistory(request, HistoryAction.SUBMITTED, null, client, null);
        return toDto(request);
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENT')")
    public ClientRequestResponseDto editAndResubmit(String clientUsername, Long requestId, CreateClientRequestDto dto) {
        User client = getUser(clientUsername);
        ClientRequest request = getRequestOrThrow(requestId);

        if (request.getStatus() != RequestStatus.REJECTED) {
            throw new InvalidStateException("Only a REJECTED request can be edited and resubmitted");
        }
        if (!request.getClient().getId().equals(client.getId())) {
            throw new AccessDeniedCustomException("You can only edit your own requests");
        }

        // Clear rejection fields and reset to PENDING_ENTRY
        request.setStatus(RequestStatus.PENDING_ENTRY);
        request.setCurrentStage(ApprovalStage.ENTRY);
        request.setRejectionStage(null);
        request.setRejectionComment(null);

        // Update fund investments
        request.getFundInvestments().clear();
        requestFundRepository.deleteByRequestId(requestId);
        attachFundsToRequest(request, dto.fundInvestments());

        requestRepository.save(request);
        recordHistory(request, HistoryAction.RESUBMITTED, null, client, null);
        return toDto(request);
    }

    @PreAuthorize("isAuthenticated()")
    public List<ClientRequestResponseDto> getMyRequests(String clientUsername) {
        User client = getUser(clientUsername);
        return requestRepository.findByClientIdOrderByCreatedAtDesc(client.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ==================== Read endpoints (shared) ====================

    @PreAuthorize("isAuthenticated()")
    public ClientRequestResponseDto getById(String username, Role role, Long id) {
        ClientRequest request = getRequestOrThrow(id);
        // Clients can only see their own; managers can see any
        if (role == Role.CLIENT && !request.getClient().getUsername().equals(username)) {
            throw new AccessDeniedCustomException("You can only view your own requests");
        }
        return toDto(request);
    }

    // ==================== Manager queue ====================

    @PreAuthorize("hasAnyRole('ENTRY_MANAGER','ASSISTANT_MANAGER','MANAGER')")
    public List<ClientRequestResponseDto> getQueueForRole(Role role) {
        RequestStatus pendingStatus = switch (role) {
            case ENTRY_MANAGER -> RequestStatus.PENDING_ENTRY;
            case ASSISTANT_MANAGER -> RequestStatus.PENDING_ASSISTANT_MANAGER;
            case MANAGER -> RequestStatus.PENDING_MANAGER;
            default -> throw new IllegalArgumentException("Not a valid approver role: " + role);
        };
        return requestRepository.findByStatusOrderByCreatedAtAsc(pendingStatus)
                .stream()
                .map(this::toDto)
                .toList();
    }
// ==================== Approval actions ====================

    @Transactional
    @PreAuthorize("hasAnyRole('ENTRY_MANAGER','ASSISTANT_MANAGER','MANAGER')")
    public ClientRequestResponseDto approve(String approverUsername, Long requestId,
                                            CreateClientRequestDto enteredData) {
        User approver = getUser(approverUsername);
        ClientRequest request = getRequestOrThrow(requestId);
        ApprovalStage stage = assertApproverMatchesCurrentStage(request, approver);

        // ENTRY stage: fund data is required (the entry manager enters/modifies it)
        if (stage == ApprovalStage.ENTRY) {
            if (enteredData == null || enteredData.fundInvestments() == null || enteredData.fundInvestments().isEmpty()) {
                throw new IllegalArgumentException(
                        "ENTRY_MANAGER must provide fund investment details when approving");
            }
            // Replace existing fund investments with the entered data
            request.getFundInvestments().clear();
            requestFundRepository.deleteByRequestId(requestId);
            attachFundsToRequest(request, enteredData.fundInvestments());
        } else {
            // Non-ENTRY stages: no body expected
            if (enteredData != null) {
                throw new IllegalArgumentException(
                        "Fund investment details can only be provided at the ENTRY stage");
            }
        }

        ApprovalStage nextStage = stage.next();
        if (nextStage == null) {
            // Final (MANAGER) approval -> mark APPROVED and create/update permanent client
            request.setStatus(RequestStatus.APPROVED);
            request.setCurrentStage(null);
            requestRepository.save(request);
            recordHistory(request, HistoryAction.APPROVED, stage, approver, null);
            createOrUpdatePermanentClient(request);
        } else {
            request.setStatus(RequestStatus.pendingFor(nextStage));
            request.setCurrentStage(nextStage);
            requestRepository.save(request);
            recordHistory(request, HistoryAction.APPROVED, stage, approver, null);
        }

        return toDto(request);
    }

    /** Convenience overload for non-ENTRY approvals (no data payload). */
    @Transactional
    @PreAuthorize("hasAnyRole('ASSISTANT_MANAGER','MANAGER')")
    public ClientRequestResponseDto approve(String approverUsername, Long requestId) {
        return approve(approverUsername, requestId, null);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ENTRY_MANAGER','ASSISTANT_MANAGER','MANAGER')")
    public ClientRequestResponseDto reject(String approverUsername, Long requestId, ApprovalActionDto dto) {
        User approver = getUser(approverUsername);
        ClientRequest request = getRequestOrThrow(requestId);
        ApprovalStage stage = assertApproverMatchesCurrentStage(request, approver);

        request.setStatus(RequestStatus.REJECTED);
        request.setCurrentStage(null);
        request.setRejectionStage(stage);
        request.setRejectionComment(dto.comment());
        requestRepository.save(request);

        recordHistory(request, HistoryAction.REJECTED, stage, approver, dto.comment());
        return toDto(request);
    }

    // ==================== Permanent client queries ====================

    @PreAuthorize("isAuthenticated()")
    public List<ClientResponseDto> getAllPermanentClients() {
        return clientRepository.findAll().stream()
                .map(this::toClientDto)
                .toList();
    }

    @PreAuthorize("isAuthenticated()")
    public ClientResponseDto getPermanentClientById(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + id));
        return toClientDto(client);
    }

    @PreAuthorize("isAuthenticated()")
    public List<ClientFundInvestmentDto> getInvestmentsByFund(Long fundId) {
        Fund fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new ResourceNotFoundException("Fund not found: " + fundId));
        return fundInvestmentRepository.findByFundId(fundId).stream()
                .map(inv -> new ClientFundInvestmentDto(
                        inv.getId(),
                        inv.getFund().getId(),
                        inv.getFund().getName(),
                        inv.getFund().getSlug(),
                        inv.getAmount(),
                        inv.getSourceRequest().getId()))
                .toList();
    }

    @PreAuthorize("isAuthenticated()")
    public List<ClientFundInvestmentDto> getClientInvestments(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + clientId));
        return client.getFundInvestments().stream()
                .map(inv -> new ClientFundInvestmentDto(
                        inv.getId(),
                        inv.getFund().getId(),
                        inv.getFund().getName(),
                        inv.getFund().getSlug(),
                        inv.getAmount(),
                        inv.getSourceRequest().getId()))
                .toList();
    }
// ==================== Helpers ====================

    /**
     * Attaches fund investment entries to a request from the DTO list.
     * Replaces any existing investments for the request.
     */
    private void attachFundsToRequest(ClientRequest request, List<FundInvestmentDto> investments) {
        for (FundInvestmentDto inv : investments) {
            Fund fund = fundRepository.findById(inv.fundId())
                    .orElseThrow(() -> new ResourceNotFoundException("Fund not found: " + inv.fundId()));
            ClientRequestFund crf = ClientRequestFund.builder()
                    .request(request)
                    .fund(fund)
                    .amount(inv.amount())
                    .build();
            request.getFundInvestments().add(crf);
        }
        requestRepository.save(request);
    }

    /**
     * Creates a permanent client record (if first approval) or adds fund investments
     * to an existing client record (for subsequent approvals).
     */
    private void createOrUpdatePermanentClient(ClientRequest request) {
        User user = request.getClient();

        // Check if a permanent client already exists for this user
        Client client = clientRepository.findByUserId(user.getId()).orElse(null);

        if (client == null) {
            // First time: create permanent client record.
            // Personal details default from User account; NIC/address/DOB can be filled
            // from the first approved request's fund context (or left for later enrichment).
            client = Client.builder()
                    .user(user)
                    .name(user.getFullName())
                    .nic("")
                    .address("")
                    .dateOfBirth(java.time.LocalDate.of(1900, 1, 1))
                    .build();
            client = clientRepository.save(client);
        }

        // Add fund investments from the approved request
        for (ClientRequestFund crf : request.getFundInvestments()) {
            ClientFundInvestment inv = ClientFundInvestment.builder()
                    .client(client)
                    .fund(crf.getFund())
                    .amount(crf.getAmount())
                    .sourceRequest(request)
                    .build();
            client.getFundInvestments().add(inv);
        }
        clientRepository.save(client);
    }

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

    private void recordHistory(ClientRequest request, HistoryAction action, ApprovalStage stage,
                               User performedBy, String comment) {
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

        // Determine saved client ID (non-null only after MANAGER approval)
        Long savedClientId = null;
        if (r.getStatus() == RequestStatus.APPROVED) {
            Client permanentClient = clientRepository.findByUserId(r.getClient().getId()).orElse(null);
            if (permanentClient != null) {
                savedClientId = permanentClient.getId();
            }
        }

        return new ClientRequestResponseDto(
                r.getId(),
                r.getClient().getUsername(),
                r.getClient().getFullName(),
                r.getClient().getEmail(),
                r.getStatus(),
                r.getCurrentStage(),
                r.getRejectionStage(),
                r.getRejectionComment(),
                r.getFundInvestments().stream()
                        .map(crf -> new FundInvestmentDto(crf.getFund().getId(), crf.getAmount()))
                        .toList(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                history,
                savedClientId
        );
    }

    private ClientResponseDto toClientDto(Client c) {
        List<ClientFundInvestmentDto> fundDtos = c.getFundInvestments().stream()
                .map(inv -> new ClientFundInvestmentDto(
                        inv.getId(),
                        inv.getFund().getId(),
                        inv.getFund().getName(),
                        inv.getFund().getSlug(),
                        inv.getAmount(),
                        inv.getSourceRequest().getId()))
                .toList();

        return new ClientResponseDto(
                c.getId(),
                c.getUser().getId(),
                c.getName(),
                c.getNic(),
                c.getAddress(),
                c.getDateOfBirth(),
                fundDtos,
                c.getApprovedAt()
        );
    }
}
