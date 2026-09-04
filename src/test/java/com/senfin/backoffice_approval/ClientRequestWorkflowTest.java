package com.senfin.backoffice_approval;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.senfin.backoffice_approval.dto.FundInvestmentDto;
import com.senfin.backoffice_approval.entity.ApprovalStage;
import com.senfin.backoffice_approval.entity.Fund;
import com.senfin.backoffice_approval.entity.RequestStatus;
import com.senfin.backoffice_approval.entity.Role;
import com.senfin.backoffice_approval.entity.User;
import com.senfin.backoffice_approval.repository.FundRepository;
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
    @Autowired
    private FundRepository fundRepository;

    private Long fundIdA;
    private Long fundIdB;

    @BeforeEach
    void setUp() {
        fundIdA = fundRepository.save(
                Fund.builder().name("Test Fund A").slug("test-fund-a").url("https://example.com/a").build()).getId();
        fundIdB = fundRepository.save(
                Fund.builder().name("Test Fund B").slug("test-fund-b").url("https://example.com/b").build()).getId();

        createUser("client_a", Role.CLIENT);
        createUser("entry_a", Role.ENTRY_MANAGER);
        createUser("assist_a", Role.ASSISTANT_MANAGER);
        createUser("mgr_a", Role.MANAGER);
    }

    @AfterEach
    void tearDown() {
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

    private void loginAs(String username, Role role) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private CreateClientRequestDto sampleRequest() {
        return new CreateClientRequestDto(List.of(
                new FundInvestmentDto(fundIdA, new BigDecimal("10000.00"))));
    }

    @Test
    void happyPath_allThreeApprovals_endsApproved() {
        loginAs("client_a", Role.CLIENT);
        ClientRequestResponseDto req = requestService.submit("client_a", sampleRequest());
        assertEquals(RequestStatus.PENDING_ENTRY, req.status());
        assertEquals(1, req.fundInvestments().size());
        assertEquals(fundIdA, req.fundInvestments().get(0).fundId());

        loginAs("entry_a", Role.ENTRY_MANAGER);
        req = requestService.approve("entry_a", req.id(), sampleRequest());
        assertEquals(RequestStatus.PENDING_ASSISTANT_MANAGER, req.status());
        assertEquals(1, req.fundInvestments().size());

        loginAs("assist_a", Role.ASSISTANT_MANAGER);
        req = requestService.approve("assist_a", req.id());
        assertEquals(RequestStatus.PENDING_MANAGER, req.status());

        loginAs("mgr_a", Role.MANAGER);
        req = requestService.approve("mgr_a", req.id());
        assertEquals(RequestStatus.APPROVED, req.status());
        assertNull(req.currentStage());
        assertEquals(4, req.history().size());
        assertNotNull(req.savedClientId());
        assertEquals(1, req.fundInvestments().size());
    }

    @Test
    void rejectionAtEntry_recordsStageAndComment() {
        loginAs("client_a", Role.CLIENT);
        ClientRequestResponseDto req = requestService.submit("client_a", sampleRequest());

        loginAs("entry_a", Role.ENTRY_MANAGER);
        req = requestService.reject("entry_a", req.id(), new ApprovalActionDto("Fund details incomplete"));

        assertEquals(RequestStatus.REJECTED, req.status());
        assertEquals(ApprovalStage.ENTRY, req.rejectionStage());
        assertEquals("Fund details incomplete", req.rejectionComment());
    }

    @Test
    void wrongStageApprover_isRejectedByAuthorization() {
        loginAs("client_a", Role.CLIENT);
        ClientRequestResponseDto req = requestService.submit("client_a", sampleRequest());
        Long id = req.id();

        loginAs("assist_a", Role.ASSISTANT_MANAGER);
        assertThrows(com.senfin.backoffice_approval.exception.AccessDeniedCustomException.class,
                () -> requestService.approve("assist_a", id));
    }
@Test
    void clientCanEditOnlyAfterRejection_andItRestartsAtEntry() {
        loginAs("client_a", Role.CLIENT);
        ClientRequestResponseDto req = requestService.submit("client_a", sampleRequest());
        Long id = req.id();

        assertThrows(com.senfin.backoffice_approval.exception.InvalidStateException.class,
                () -> requestService.editAndResubmit("client_a", id, sampleRequest()));

        loginAs("entry_a", Role.ENTRY_MANAGER);
        requestService.approve("entry_a", id, sampleRequest());

        loginAs("assist_a", Role.ASSISTANT_MANAGER);
        requestService.reject("assist_a", id, new ApprovalActionDto("Insufficient investment amount"));

        loginAs("client_a", Role.CLIENT);
        CreateClientRequestDto editedDto = new CreateClientRequestDto(List.of(
                new FundInvestmentDto(fundIdB, new BigDecimal("20000.00"))));
        ClientRequestResponseDto edited = requestService.editAndResubmit("client_a", id, editedDto);

        assertEquals(RequestStatus.PENDING_ENTRY, edited.status());
        assertEquals(ApprovalStage.ENTRY, edited.currentStage());
        assertNull(edited.rejectionStage());
        assertNull(edited.rejectionComment());
        assertEquals(1, edited.fundInvestments().size());
        assertEquals(fundIdB, edited.fundInvestments().get(0).fundId());
    }

    @Test
    void entryManagerCannotApproveWithoutEnteringFundDetails() {
        loginAs("client_a", Role.CLIENT);
        ClientRequestResponseDto req = requestService.submit("client_a", sampleRequest());
        Long id = req.id();

        loginAs("entry_a", Role.ENTRY_MANAGER);
        assertThrows(IllegalArgumentException.class, () -> requestService.approve("entry_a", id, null));
    }

    @Test
    void permanentClientOnlyExistsAfterFinalApproval() {
        loginAs("client_a", Role.CLIENT);
        ClientRequestResponseDto req = requestService.submit("client_a", sampleRequest());
        assertNull(req.savedClientId());

        loginAs("entry_a", Role.ENTRY_MANAGER);
        req = requestService.approve("entry_a", req.id(), sampleRequest());
        assertNull(req.savedClientId(), "still just staging data -- not permanent yet");

        loginAs("assist_a", Role.ASSISTANT_MANAGER);
        req = requestService.approve("assist_a", req.id());
        assertNull(req.savedClientId(), "still not permanent until the MANAGER stage");

        loginAs("mgr_a", Role.MANAGER);
        req = requestService.approve("mgr_a", req.id());
        assertNotNull(req.savedClientId(), "now permanently saved");
    }
}
