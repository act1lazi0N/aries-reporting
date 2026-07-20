package com.actilazion.ariesreportingproject.service.export;

import com.actilazion.ariesreportingproject.dto.request.ExportRequest;
import com.actilazion.ariesreportingproject.dto.response.ReportJobResponse;
import com.actilazion.ariesreportingproject.entity.reporting.ReportJob;
import com.actilazion.ariesreportingproject.enums.ReportJobStatus;
import com.actilazion.ariesreportingproject.exception.ReportJobNotReadyException;
import com.actilazion.ariesreportingproject.exception.ResourceNotFoundException;
import com.actilazion.ariesreportingproject.repository.reporting.ReportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportOrchestrator {
    private final ReportJobRepository reportJobRepository;
    private final ExportJobProcessor exportJobProcessor;

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
        exportJobProcessor.processJob(job.getId());

        log.info("[EXPORT] Job created jobId={} format={}", job.getId(), request.format());
        return ReportJobResponse.from(job, baseUrl);
    }

    @Transactional(readOnly = true)
    public ReportJobResponse getStatus(
            UUID jobId,
            UUID requestedBy,
            boolean isAdmin,
            String baseUrl) {
        ReportJob job = reportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("ReportJob", jobId));
        requireJobAccess(job, requestedBy, isAdmin);
        return ReportJobResponse.from(job, baseUrl);
    }

    @Transactional
    public Path getFileForDownload(UUID jobId, UUID requestedBy, boolean isAdmin) {
        ReportJob job = reportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("ReportJob", jobId));
        requireJobAccess(job, requestedBy, isAdmin);

        if (job.getStatus() != ReportJobStatus.READY) {
            throw new ReportJobNotReadyException(jobId.toString());
        }

        if (job.getExpiresAt() != null && OffsetDateTime.now().isAfter(job.getExpiresAt())) {
            job.setStatus(ReportJobStatus.EXPIRED);
            reportJobRepository.save(job);
            throw new ResourceNotFoundException("Report file expired", jobId);
        }

        return Paths.get(job.getFilePath());
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredJobs() {
        List<ReportJob> expired = reportJobRepository.findAllByStatusAndExpiresAtBefore(
                ReportJobStatus.READY, OffsetDateTime.now());
        for (ReportJob job : expired) {
            try {
                if (job.getFilePath() != null) {
                    Files.deleteIfExists(Paths.get(job.getFilePath()));
                }
                job.setStatus(ReportJobStatus.EXPIRED);
                reportJobRepository.save(job);
                log.info("[EXPORT] Expired jobId={}", job.getId());
            } catch (IOException e) {
                log.error("[EXPORT] Failed to delete file for jobId={}: {}",
                        job.getId(), e.getMessage());
            }
        }

        if (!expired.isEmpty()) {
            log.info("[EXPORT] Cleaned up {} expired jobs", expired.size());
        }
    }

    private void requireJobAccess(ReportJob job, UUID requestedBy, boolean isAdmin) {
        if (!isAdmin && !job.getRequestedBy().equals(requestedBy)) {
            throw new AccessDeniedException("Access denied to export job");
        }
    }
}
