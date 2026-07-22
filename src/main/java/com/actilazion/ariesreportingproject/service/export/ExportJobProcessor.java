package com.actilazion.ariesreportingproject.service.export;

import com.actilazion.ariesreportingproject.config.AppProperties;
import com.actilazion.ariesreportingproject.dto.response.AccountStatementResponse;
import com.actilazion.ariesreportingproject.entity.reporting.ReportJob;
import com.actilazion.ariesreportingproject.enums.ReportJobStatus;
import com.actilazion.ariesreportingproject.repository.reporting.ReportJobRepository;
import com.actilazion.ariesreportingproject.service.reporting.StatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportJobProcessor {
    private static final int MAX_EXPORT_ROWS = 10_000;

    private final ReportJobRepository reportJobRepository;
    private final StatementService statementService;
    private final ExcelExportService excelExportService;
    private final PdfExportService pdfExportService;
    private final AppProperties appProperties;

    @Async("exportTaskExecutor")
    public void processJob(UUID jobId) {
        ReportJob job = reportJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }

        job.setStatus(ReportJobStatus.PROCESSING);
        reportJobRepository.save(job);

        try {
            UUID accountId = UUID.fromString(job.getParams().get("accountId").toString());
            YearMonth from = YearMonth.parse(job.getParams().get("from").toString());
            YearMonth to = YearMonth.parse(job.getParams().get("to").toString());

            AccountStatementResponse statement = statementService.getStatement(
                    accountId, from, to, PageRequest.of(0, MAX_EXPORT_ROWS));

            Path exportDir = Paths.get(appProperties.getExportDir());
            Files.createDirectories(exportDir);

            String filename = buildFilename(job);
            Path filePath = exportDir.resolve(filename);

            switch (job.getFormat()) {
                case EXCEL -> excelExportService.generateStatement(statement, filePath);
                case PDF -> pdfExportService.generateStatement(statement, filePath);
            }

            job.setStatus(ReportJobStatus.READY);
            job.setFilePath(filePath.toString());
            job.setCompletedAt(OffsetDateTime.now());
            job.setExpiresAt(OffsetDateTime.now().plusHours(appProperties.getExportTtlHours()));

            log.info("[EXPORT] Job completed jobId={} file={}", jobId, filename);
        } catch (Exception e) {
            job.setStatus(ReportJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            log.error("[EXPORT] Job failed jobId={}: {}", jobId, e.getMessage(), e);
        }

        reportJobRepository.save(job);
    }

    private String buildFilename(ReportJob job) {
        String ext = switch (job.getFormat()) {
            case EXCEL -> "xlsx";
            case PDF -> "pdf";
        };
        return "report_" + job.getId() + "." + ext;
    }
}
