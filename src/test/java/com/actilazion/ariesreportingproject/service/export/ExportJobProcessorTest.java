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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.persistence.OptimisticLockException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

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

    @Test
    @DisplayName("processJob: fails oversized exports instead of silently truncating")
    void processJob_failsOversizedExport() throws Exception {
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
        when(statementService.getStatement(any(), any(), any(), any()))
                .thenReturn(AccountStatementResponse.builder()
                        .accountId(accountId)
                        .periodFrom("2026-01")
                        .periodTo("2026-01")
                        .totalDebit(BigDecimal.ZERO)
                        .totalCredit(BigDecimal.ZERO)
                        .netFlow(BigDecimal.ZERO)
                        .txCount(0)
                        .transactions(new PageImpl<>(
                                java.util.List.of(),
                                PageRequest.of(0, 10_000),
                                10_001))
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
        assertThat(job.getStatus()).isEqualTo(ReportJobStatus.FAILED);
        assertThat(job.getErrorMessage())
                .isEqualTo("Export exceeds 10000 rows; narrow the requested period");
        verify(excelExportService, never()).generateStatement(any(), any());
    }

    @Test
    @DisplayName("processJob: rejects unsupported report job types without dispatching")
    void processJob_rejectsUnsupportedJobType() throws Exception {
        UUID jobId = UUID.randomUUID();
        ReportJob job = ReportJob.builder()
                .id(jobId)
                .jobType(ReportJobType.ADMIN_OVERVIEW)
                .format(ReportFormat.PDF)
                .status(ReportJobStatus.PENDING)
                .params(Map.of())
                .build();

        when(reportJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        ExportJobProcessor processor = new ExportJobProcessor(
                reportJobRepository,
                statementService,
                excelExportService,
                pdfExportService,
                appProperties);

        processor.processJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ReportJobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("Unsupported report job type");
        verifyNoGeneratorDispatch();
    }

    @Test
    @DisplayName("processJob: stores stable public message for unexpected generator errors")
    void processJob_sanitizesUnexpectedErrors() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ReportJob job = ReportJob.builder()
                .id(jobId)
                .jobType(ReportJobType.ACCOUNT_STATEMENT)
                .format(ReportFormat.PDF)
                .status(ReportJobStatus.PENDING)
                .params(Map.of(
                        "accountId", accountId.toString(),
                        "from", "2026-01",
                        "to", "2026-01"))
                .build();

        when(reportJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(statementService.getStatement(any(), any(), any(), any()))
                .thenReturn(AccountStatementResponse.builder()
                        .accountId(accountId)
                        .periodFrom("2026-01")
                        .periodTo("2026-01")
                        .totalDebit(BigDecimal.ZERO)
                        .totalCredit(BigDecimal.ZERO)
                        .netFlow(BigDecimal.ZERO)
                        .txCount(0)
                        .transactions(new PageImpl<>(
                                java.util.List.of(),
                                PageRequest.of(0, 10_000),
                                0))
                        .build());
        when(appProperties.getExportDir()).thenReturn("target/test-exports");
        doThrow(new IllegalStateException("internal path C:\\secret\\report.pdf"))
                .when(pdfExportService).generateStatement(any(), any());

        ExportJobProcessor processor = new ExportJobProcessor(
                reportJobRepository,
                statementService,
                excelExportService,
                pdfExportService,
                appProperties);

        processor.processJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ReportJobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("Export failed");
    }

    @Test
    @DisplayName("processJob: skips generation when another instance wins the claim")
    void processJob_skipsWhenClaimLost() {
        UUID jobId = UUID.randomUUID();
        ReportJob job = ReportJob.builder()
                .id(jobId)
                .jobType(ReportJobType.ACCOUNT_STATEMENT)
                .format(ReportFormat.PDF)
                .status(ReportJobStatus.PROCESSING)
                .params(Map.of())
                .build();

        when(reportJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        doThrow(new OptimisticLockException())
                .when(reportJobRepository).saveAndFlush(job);

        ExportJobProcessor processor = new ExportJobProcessor(
                reportJobRepository,
                statementService,
                excelExportService,
                pdfExportService,
                appProperties);

        processor.processJob(jobId);

        verify(statementService, never()).getStatement(any(), any(), any(), any());
        verify(reportJobRepository, never()).save(job);
    }

    @Test
    @DisplayName("processJob: does not reopen terminal jobs")
    void processJob_skipsTerminalJob() {
        UUID jobId = UUID.randomUUID();
        ReportJob job = ReportJob.builder()
                .id(jobId)
                .status(ReportJobStatus.READY)
                .build();

        when(reportJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        ExportJobProcessor processor = new ExportJobProcessor(
                reportJobRepository,
                statementService,
                excelExportService,
                pdfExportService,
                appProperties);

        processor.processJob(jobId);

        verify(reportJobRepository, never()).saveAndFlush(any());
        verify(statementService, never()).getStatement(any(), any(), any(), any());
    }

    private void verifyNoGeneratorDispatch() throws Exception {
        verify(excelExportService, never()).generateStatement(any(), any());
        verify(pdfExportService, never()).generateStatement(any(), any());
    }
}
