package com.senfin.backoffice_approval;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.senfin.backoffice_approval.dto.ApprovalActionDto;
import com.senfin.backoffice_approval.dto.ClientRequestResponseDto;
import com.senfin.backoffice_approval.dto.CreateClientRequestDto;
import com.senfin.backoffice_approval.entity.ApprovalStage;
import com.senfin.backoffice_approval.entity.RequestStatus;
import com.senfin.backoffice_approval.entity.Role;
import com.senfin.backoffice_approval.entity.User;
import com.senfin.backoffice_approval.exception.AccessDeniedCustomException;
import com.senfin.backoffice_approval.exception.InvalidStateException;
import com.senfin.backoffice_approval.repository.UserRepository;
import com.senfin.backoffice_approval.service.ClientRequestService;

import jakarta.transaction.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClientRequestWorkflowTest {

    @Autowired
    private ClientRequestService requestService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        createUser("client_a", Role.CLIENT);
        createUser("entry_a", Role.ENTRY_MANAGER);
        createUser("assist_a", Role.ASSISTANT_MANAGER);
        createUser("mgr_a", Role.MANAGER);
    }

    @AfterEach
    void tearDown() {
        // Prevent one test's "logged in as" state from leaking into the next test.
        SecurityContextHolder.clearContext();
    }

    private void createUser(String username, Role role) {
        userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode("pw"))
                .fullName(username)
                .email(username + "@test.com")
                .role(role)
                .enabled(true)
                .build());
    }

    /**
     * @PreAuthorize needs an Authentication in the SecurityContext to check roles against.
     * A real HTTP request gets one for free from JwtAuthFilter; calling the service directly
     * in a unit test does not, so we plant one manually -- this is the test-only equivalent
     * of "logging in as" a given user before making the call.
     */
    private void loginAs(String username, Role role) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private CreateClientRequestDto sampleRequest() {
        return new CreateClientRequestDto("Test Client", "912345678V", "123 Main St", LocalDate.of(1991, 1, 1));
    }

    @Test
    void happyPath_allThreeApprovals_endsApproved() {
        loginAs("client_a", Role.CLIENT);
        ClientRequestResponseDto req = requestService.submit("client_a", sampleRequest());
        assertEquals(RequestStatus.PENDING_ENTRY, req.status());

        loginAs("entry_a", Role.ENTRY_MANAGER);
        req = requestService.approve("entry_a", req.id());
        assertEquals(RequestStatus.PENDING_ASSISTANT_MANAGER, req.status());

        loginAs("assist_a", Role.ASSISTANT_MANAGER);
        req = requestService.approve("assist_a", req.id());
        assertEquals(RequestStatus.PENDING_MANAGER, req.status());

        loginAs("mgr_a", Role.MANAGER);
        req = requestService.approve("mgr_a", req.id());
        assertEquals(RequestStatus.APPROVED, req.status());
        assertNull(req.currentStage());
        assertEquals(4, req.history().size()); // SUBMITTED + 3 APPROVED
    }

    @Test
    void rejectionAtEntry_recordsStageAndComment() {
        loginAs("client_a", Role.CLIENT);
        ClientRequestResponseDto req = requestService.submit("client_a", sampleRequest());

        loginAs("entry_a", Role.ENTRY_MANAGER);
        req = requestService.reject("entry_a", req.id(), new ApprovalActionDto("NIC does not match records"));

        assertEquals(RequestStatus.REJECTED, req.status());
        assertEquals(ApprovalStage.ENTRY, req.rejectionStage());
        assertEquals("NIC does not match records", req.rejectionComment());
    }

    @Test
    void wrongStageApprover_isRejectedByAuthorization() {
        loginAs("client_a", Role.CLIENT);
        ClientRequestResponseDto req = requestService.submit("client_a", sampleRequest());
        Long id = req.id();

        // request is at ENTRY stage; assistant manager trying to act should fail
        loginAs("assist_a", Role.ASSISTANT_MANAGER);
        assertThrows(AccessDeniedCustomException.class, () -> requestService.approve("assist_a", id));
    }

    @Test
    void clientCanEditOnlyAfterRejection_andItRestartsAtEntry() {
        loginAs("client_a", Role.CLIENT);
        ClientRequestResponseDto req = requestService.submit("client_a", sampleRequest());
        Long id = req.id();

        // Can't edit while still pending
        assertThrows(InvalidStateException.class, () ->
                requestService.editAndResubmit("client_a", id, sampleRequest()));

        // Reject at assistant-manager stage after passing entry
        loginAs("entry_a", Role.ENTRY_MANAGER);
        requestService.approve("entry_a", id);

        loginAs("assist_a", Role.ASSISTANT_MANAGER);
        requestService.reject("assist_a", id, new ApprovalActionDto("Address unverifiable"));

        loginAs("client_a", Role.CLIENT);
        ClientRequestResponseDto edited = requestService.editAndResubmit("client_a", id,
                new CreateClientRequestDto("Test Client", "912345678V", "456 New Rd", LocalDate.of(1991, 1, 1)));

        assertEquals(RequestStatus.PENDING_ENTRY, edited.status());
        assertEquals(ApprovalStage.ENTRY, edited.currentStage());
        assertNull(edited.rejectionStage());
        assertNull(edited.rejectionComment());
        assertEquals("456 New Rd", edited.address());
    }
}