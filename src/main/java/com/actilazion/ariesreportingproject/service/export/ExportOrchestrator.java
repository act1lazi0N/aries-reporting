package com.actilazion.ariesreportingproject.service.export;

import com.actilazion.ariesreportingproject.config.AppProperties;
import com.actilazion.ariesreportingproject.dto.request.ExportRequest;
import com.actilazion.ariesreportingproject.dto.response.AccountStatementResponse;
import com.actilazion.ariesreportingproject.dto.response.ReportJobResponse;
import com.actilazion.ariesreportingproject.entity.reporting.ReportJob;
import com.actilazion.ariesreportingproject.enums.ReportJobStatus;
import com.actilazion.ariesreportingproject.exception.ReportJobNotReadyException;
import com.actilazion.ariesreportingproject.exception.ResourceNotFoundException;
import com.actilazion.ariesreportingproject.repository.reporting.ReportJobRepository;
import com.actilazion.ariesreportingproject.service.reporting.StatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportOrchestrator {
    private final ReportJobRepository reportJobRepository;
    private final StatementService statementService;
    private final ExcelExportService excelExportService;
    private final PdfExportService pdfExportService;
    private final AppProperties appProperties;

    // Step 1: Create jobs and immediately return - no need to wait
    @Transactional
    public ReportJobResponse createJob(ExportRequest request, UUID requestedBy, String baseUrl) {
        Map<String, Object> params = new HashMap<>();
        params.put("accountId", request.accountId().toString());
        params.put("from", request.from());
        params.put("to", request.to());

        ReportJob job = ReportJob.builder()
                .requestedBy(requestedBy)
                .jobType(request.jobType())
                .format(request.format())
                .params(params)
                .status(ReportJobStatus.PENDING)
                .build();

        job = reportJobRepository.save(job);

        // Kick-off async processing
        processJob(job.getId());

        log.info("[EXPORT] Job created jobId={} format={}", job.getId(), request.format());
        return ReportJobResponse.from(job, baseUrl);
    }

    // Step 2: Async generate - run on exportTaskExecutor thread pool
    @Async("exportTaskExecutor")
    public void processJob(UUID jobId) {
        ReportJob job = reportJobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        // UPDATE PROCESSING
        job.setStatus(ReportJobStatus.PROCESSING);
        reportJobRepository.save(job);

        try {
            // Get params from JSONB
            UUID accountId = UUID.fromString(job.getParams().get("accountId").toString());
            YearMonth from = YearMonth.parse(job.getParams().get("from").toString());
            YearMonth to = YearMonth.parse(job.getParams().get("to").toString());

            // Fetch data
            AccountStatementResponse statement = statementService.getStatement(accountId, from, to, PageRequest.of(0, Integer.MAX_VALUE));

            // Create export directory if not exists
            Path exportDir = Paths.get(appProperties.getExportDir());
            Files.createDirectories(exportDir);

            // Generate file following with format
            String filename = buildFilename(job);
            Path filePath = exportDir.resolve(filename);

            switch (job.getFormat()) {
                case EXCEL -> excelExportService.generateStatement(statement, filePath);
                case PDF -> pdfExportService.generateStatement(statement, filePath);
            }

            // UPDATE READY
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

    // Step 3: Client poll status
    @Transactional(readOnly = true)
    public ReportJobResponse getStatus(UUID jobId, String baseUrl) {
        ReportJob job = reportJobRepository.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("ReportJob", jobId));
        return ReportJobResponse.from(job, baseUrl);
    }

    // Step 4: Stream file for client
    // Controller calls this function to take path, then stream response
    @Transactional(readOnly = true)
    public Path getFileForDownload(UUID jobId) {
        ReportJob job = reportJobRepository.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("ReportJob", jobId));

        if (job.getStatus() != ReportJobStatus.READY)
            throw new ReportJobNotReadyException(jobId.toString());

        if (job.getExpiresAt() != null && OffsetDateTime.now().isAfter(job.getExpiresAt())) {
            job.setStatus(ReportJobStatus.EXPIRED);
            reportJobRepository.save(job);
            throw new ResourceNotFoundException("Report file expired", jobId);
        }

        return Paths.get(job.getFilePath());
    }

    // Step 5: Cleanup job ran each hour - delete expired jobs
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredJobs() {
        List<ReportJob> expired = reportJobRepository.findAllByStatusAndExpiresAtBefore(ReportJobStatus.READY, OffsetDateTime.now());
        for (ReportJob job : expired) {
            try {
                // Delete physical file
                if (job.getFilePath() != null) {
                    Files.deleteIfExists(Paths.get(job.getFilePath()));
                }
                job.setStatus(ReportJobStatus.EXPIRED);
                reportJobRepository.save(job);
                log.info("[EXPORT] Expired jobId={}", job.getId());
            } catch (IOException e) {
                log.error("[EXPORT] Failed to delete file for jobId={}: {}", job.getId(), e.getMessage());
            }
        }

        if (!expired.isEmpty()) {
            log.info("[EXPORT] Cleaned up {} expired jobs", expired.size());
        }
    }

    // Helper
    private String buildFilename(ReportJob job) {
        String ext = switch (job.getFormat()) {
            case EXCEL -> "xlsx";
            case PDF   -> "pdf";
        };
        return "report_" + job.getId() + "." + ext;
    }


}
