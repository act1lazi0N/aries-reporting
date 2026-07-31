package com.actilazion.ariesreportingproject.controller;

import com.actilazion.ariesreportingproject.entity.reporting.ReportingTransaction;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
    @DisplayName("getStatement: rejects an unbounded month range")
    void getStatement_excessiveMonthRange_throwsBadRequestCause() {
        assertThatThrownBy(() -> controller.getStatement(
                UUID.randomUUID(), "2020-01", "2030-01", PageRequest.of(0, 20), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("statement period must not exceed 120 months");
    }

    @Test
    @DisplayName("getStatement: rejects months before the reporting retention boundary")
    void getStatement_beforeMinimumYear_throwsBadRequestCause() {
        assertThatThrownBy(() -> controller.getStatement(
                UUID.randomUUID(), "2019-12", "2020-01", PageRequest.of(0, 20), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("from year must be greater than or equal to 2020");
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
    @DisplayName("getTopTransactions: returns DTOs instead of JPA entities")
    void getTopTransactions_returnsDtos() {
        UUID accountId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        UUID originalTxId = UUID.randomUUID();
        OffsetDateTime from = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-08-01T00:00:00Z");
        ReportingTransaction tx = ReportingTransaction.builder()
                .id(txId)
                .originalTxId(originalTxId)
                .fromAccountNumber("1001")
                .toAccountNumber("2002")
                .fromOwnerName("Sender")
                .toOwnerName("Receiver")
                .amount(new BigDecimal("999.00"))
                .currency("VND")
                .status("COMPLETED")
                .description("top")
                .createdAt(from.plusDays(1))
                .build();

        when(statementService.getTopTransactions(accountId, from, to, 5))
                .thenReturn(List.of(tx));

        var response = controller.getTopTransactions(accountId, from, to, 5, null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().data().getFirst().id()).isEqualTo(txId);
        assertThat(response.getBody().data().getFirst().originalTxId()).isEqualTo(originalTxId);
        assertThat(response.getBody().data().getFirst().amount()).isEqualByComparingTo("999.00");
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
