package com.actilazion.ariesreportingproject.service.export;

import com.actilazion.ariesreportingproject.dto.request.ExportRequest;
import com.actilazion.ariesreportingproject.dto.response.ReportJobResponse;
import com.actilazion.ariesreportingproject.entity.reporting.ReportJob;
import com.actilazion.ariesreportingproject.enums.ReportFormat;
import com.actilazion.ariesreportingproject.enums.ReportJobStatus;
import com.actilazion.ariesreportingproject.enums.ReportJobType;
import com.actilazion.ariesreportingproject.exception.ReportJobNotReadyException;
import com.actilazion.ariesreportingproject.repository.reporting.ReportJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportOrchestratorTest {
    @Mock
    ReportJobRepository reportJobRepo;
    @Mock
    ExportJobProcessor exportJobProcessor;
    @InjectMocks
    ExportOrchestrator orchestrator;

    @Test
    @DisplayName("createJob: creates job with PENDING status and returns immediately")
    void createJob_returnsPendingJob() {
        UUID requestedBy = UUID.randomUUID();
        ExportRequest request = new ExportRequest(UUID.randomUUID(), ReportJobType.ACCOUNT_STATEMENT, ReportFormat.EXCEL, "2025-01", "2025-03");

        ReportJob savedJob = ReportJob.builder().id(UUID.randomUUID()).status(ReportJobStatus.PENDING).jobType(request.jobType()).format(request.format()).createdAt(OffsetDateTime.now()).build();

        when(reportJobRepo.save(any())).thenReturn(savedJob);

        ReportJobResponse response = orchestrator.createJob(request, requestedBy, "http://localhost:8081");

        assertThat(response.status()).isEqualTo(ReportJobStatus.PENDING);
        assertThat(response.downloadUrl()).isNull(); // not READY yet
        verify(reportJobRepo).save(any());
        verify(exportJobProcessor).processJob(savedJob.getId());
    }

    @Test
    @DisplayName("getFileForDownload: throws when job is not READY")
    void getFileForDownload_throwsWhenNotReady() {
        UUID jobId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        ReportJob pendingJob = ReportJob.builder().id(jobId).requestedBy(requestedBy).status(ReportJobStatus.PROCESSING).build();

        when(reportJobRepo.findById(jobId)).thenReturn(Optional.of(pendingJob));

        assertThatThrownBy(() -> orchestrator.getFileForDownload(jobId, requestedBy, false)).isInstanceOf(ReportJobNotReadyException.class);
    }

    @Test
    @DisplayName("getFileForDownload: throws when job has EXPIRED")
    void getFileForDownload_throwsWhenExpired() {
        UUID jobId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        ReportJob expiredJob = ReportJob.builder().id(jobId).requestedBy(requestedBy).status(ReportJobStatus.READY).filePath("/tmp/report.xlsx").expiresAt(OffsetDateTime.now().minusHours(1)) // already expired
                .build();

        when(reportJobRepo.findById(jobId)).thenReturn(Optional.of(expiredJob));
        when(reportJobRepo.save(any())).thenReturn(expiredJob);

        assertThatThrownBy(() -> orchestrator.getFileForDownload(jobId, requestedBy, false)).isInstanceOf(Exception.class);

        // Status must be updated to EXPIRED
        verify(reportJobRepo).save(argThat(job -> job.getStatus() == ReportJobStatus.EXPIRED));
    }

    @Test
    @DisplayName("cleanupExpiredJobs: updates expired jobs to EXPIRED")
    void cleanupExpiredJobs_updatesExpiredStatus() {
        ReportJob expiredJob = ReportJob.builder().id(UUID.randomUUID()).status(ReportJobStatus.READY).filePath("/tmp/nonexistent.xlsx").expiresAt(OffsetDateTime.now().minusHours(2)).build();

        when(reportJobRepo.findAllByStatusAndExpiresAtBefore(eq(ReportJobStatus.READY), any())).thenReturn(java.util.List.of(expiredJob));

        orchestrator.cleanupExpiredJobs();

        verify(reportJobRepo).save(argThat(job -> job.getStatus() == ReportJobStatus.EXPIRED));
    }
}
