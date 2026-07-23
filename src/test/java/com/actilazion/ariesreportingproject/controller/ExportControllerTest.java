package com.actilazion.ariesreportingproject.controller;

import com.actilazion.ariesreportingproject.dto.request.ExportRequest;
import com.actilazion.ariesreportingproject.dto.response.ReportJobResponse;
import com.actilazion.ariesreportingproject.enums.ReportFormat;
import com.actilazion.ariesreportingproject.enums.ReportJobStatus;
import com.actilazion.ariesreportingproject.enums.ReportJobType;
import com.actilazion.ariesreportingproject.service.export.ExportOrchestrator;
import com.actilazion.ariesreportingproject.service.security.UserAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.User;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportControllerTest {
    @Mock
    ExportOrchestrator exportOrchestrator;
    @Mock
    UserAccessService userAccessService;
    @InjectMocks
    ExportController controller;

    @Test
    @DisplayName("requestExport: verifies requester and account access")
    void requestExport_verifiesRequesterAndAccountAccess() {
        UUID requesterId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        var userDetails = User.withUsername("user@aries.local")
                .password("n/a")
                .roles("USER")
                .build();
        ExportRequest request = new ExportRequest(
                accountId,
                ReportJobType.ACCOUNT_STATEMENT,
                ReportFormat.PDF,
                "2026-01",
                "2026-01");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setScheme("https");
        httpRequest.setServerName("reports.local");
        httpRequest.setServerPort(8443);
        ReportJobResponse job = new ReportJobResponse(
                jobId,
                ReportJobType.ACCOUNT_STATEMENT,
                ReportJobStatus.PENDING,
                ReportFormat.PDF,
                null,
                null,
                null,
                OffsetDateTime.now(),
                null);

        when(userAccessService.requireCurrentUserId(userDetails)).thenReturn(requesterId);
        when(exportOrchestrator.createJob(
                eq(request), eq(requesterId), eq("https://reports.local:8443")))
                .thenReturn(job);

        var response = controller.requestExport(request, userDetails, httpRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().id()).isEqualTo(jobId);
        verify(userAccessService).requireAccountAccess(userDetails, accountId);
    }

    @Test
    @DisplayName("getStatus: passes requester and admin flag to orchestrator")
    void getStatus_passesRequesterAndAdminFlag() {
        UUID requesterId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        var admin = User.withUsername("admin@aries.local")
                .password("n/a")
                .roles("ADMIN")
                .build();
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setScheme("http");
        httpRequest.setServerName("localhost");
        httpRequest.setServerPort(8081);
        ReportJobResponse job = new ReportJobResponse(
                jobId,
                ReportJobType.ACCOUNT_STATEMENT,
                ReportJobStatus.READY,
                ReportFormat.EXCEL,
                "http://localhost:8081/api/v1/reports/export/" + jobId + "/download",
                null,
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now(),
                OffsetDateTime.now());

        when(userAccessService.requireCurrentUserId(admin)).thenReturn(requesterId);
        when(userAccessService.isAdmin(admin)).thenReturn(true);
        when(exportOrchestrator.getStatus(jobId, requesterId, true, "http://localhost:8081"))
                .thenReturn(job);

        var response = controller.getStatus(jobId, admin, httpRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(exportOrchestrator).getStatus(jobId, requesterId, true, "http://localhost:8081");
    }
}
