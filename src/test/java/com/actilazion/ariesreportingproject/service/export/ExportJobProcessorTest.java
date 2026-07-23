package com.actilazion.ariesreportingproject.service.export;

import com.actilazion.ariesreportingproject.config.AppProperties;
import com.actilazion.ariesreportingproject.dto.response.AccountStatementResponse;
import com.actilazion.ariesreportingproject.entity.reporting.ReportJob;
import com.actilazion.ariesreportingproject.enums.ReportFormat;
import com.actilazion.ariesreportingproject.enums.ReportJobStatus;
import com.actilazion.ariesreportingproject.enums.ReportJobType;
import com.actilazion.ariesreportingproject.repository.reporting.ReportJobRepository;
import com.actilazion.ariesreportingproject.service.reporting.StatementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportJobProcessorTest {
    @Mock
    ReportJobRepository reportJobRepository;
    @Mock
    StatementService statementService;
    @Mock
    ExcelExportService excelExportService;
    @Mock
    PdfExportService pdfExportService;
    @Mock
    AppProperties appProperties;
    @TempDir
    Path exportDir;

    @Test
    @DisplayName("processJob: uses bounded page size for statement export")
    void processJob_usesBoundedStatementPageSize() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ReportJob job = ReportJob.builder()
                .id(jobId)
                .jobType(ReportJobType.ACCOUNT_STATEMENT)
                .format(ReportFormat.EXCEL)
                .status(ReportJobStatus.PENDING)
                .params(Map.of(
                        "accountId", accountId.toString(),
                        "from", "2026-01",
                        "to", "2026-01"))
                .build();

        when(reportJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(appProperties.getExportDir()).thenReturn(exportDir.toString());
        when(appProperties.getExportTtlHours()).thenReturn(24);
        when(statementService.getStatement(any(), any(), any(), any()))
                .thenReturn(AccountStatementResponse.builder()
                        .accountId(accountId)
                        .periodFrom("2026-01")
                        .periodTo("2026-01")
                        .totalDebit(BigDecimal.ZERO)
                        .totalCredit(BigDecimal.ZERO)
                        .netFlow(BigDecimal.ZERO)
                        .txCount(0)
                        .transactions(new PageImpl<>(java.util.List.of()))
                        .build());

        ExportJobProcessor processor = new ExportJobProcessor(
                reportJobRepository,
                statementService,
                excelExportService,
                pdfExportService,
                appProperties);

        processor.processJob(jobId);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(statementService).getStatement(
                eq(accountId),
                eq(YearMonth.parse("2026-01")),
                eq(YearMonth.parse("2026-01")),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10_000);
        verify(excelExportService).generateStatement(any(), any());
    }
}
