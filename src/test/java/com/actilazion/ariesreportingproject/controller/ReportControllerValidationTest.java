package com.actilazion.ariesreportingproject.controller;

import com.actilazion.ariesreportingproject.service.reporting.AdminReportService;
import com.actilazion.ariesreportingproject.service.reporting.SpendingPatternService;
import com.actilazion.ariesreportingproject.service.reporting.StatementService;
import com.actilazion.ariesreportingproject.service.security.UserAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ReportControllerValidationTest {
    @Mock
    StatementService statementService;
    @Mock
    AdminReportService adminReportService;
    @Mock
    SpendingPatternService spendingPatternService;
    @Mock
    UserAccessService userAccessService;
    @InjectMocks
    ReportController controller;

    @Test
    @DisplayName("getStatement: rejects invalid yyyy-MM")
    void getStatement_invalidMonth_throwsBadRequestCause() {
        assertThatThrownBy(() -> controller.getStatement(
                UUID.randomUUID(), "2026-13", "2026-12", PageRequest.of(0, 20), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("from must be in format yyyy-MM");
    }

    @Test
    @DisplayName("getMonthlySummary: rejects invalid month")
    void getMonthlySummary_invalidMonth_throwsBadRequestCause() {
        assertThatThrownBy(() -> controller.getMonthlySummary(
                UUID.randomUUID(), 2026, 13, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("month must be between 1 and 12");
    }

    @Test
    @DisplayName("getTopTransactions: rejects excessive limit")
    void getTopTransactions_excessiveLimit_throwsBadRequestCause() {
        OffsetDateTime from = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-07-31T23:59:59Z");

        assertThatThrownBy(() -> controller.getTopTransactions(
                UUID.randomUUID(), from, to, 101, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 100");
    }

    @Test
    @DisplayName("getAdminOverview: rejects reversed timestamp window")
    void getAdminOverview_reversedWindow_throwsBadRequestCause() {
        OffsetDateTime from = OffsetDateTime.parse("2026-07-31T23:59:59Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-07-01T00:00:00Z");

        assertThatThrownBy(() -> controller.getAdminOverview(from, to))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("from must be before or equal to to");
    }
}
