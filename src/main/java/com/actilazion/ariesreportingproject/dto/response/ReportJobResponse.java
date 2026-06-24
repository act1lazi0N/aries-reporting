package com.actilazion.ariesreportingproject.dto.response;

import com.actilazion.ariesreportingproject.entity.reporting.ReportJob;
import com.actilazion.ariesreportingproject.enums.ReportFormat;
import com.actilazion.ariesreportingproject.enums.ReportJobStatus;
import com.actilazion.ariesreportingproject.enums.ReportJobType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportJobResponse(UUID id, ReportJobType jobType, ReportJobStatus status, ReportFormat format,
                                String downloadUrl, String errorMessage, OffsetDateTime expiresAt,
                                OffsetDateTime createdAt, OffsetDateTime completedAt) {
    public static ReportJobResponse from(ReportJob job, String baseUrl) {
        String downloadUrl = null;
        if (job.getStatus() == ReportJobStatus.READY) {
            downloadUrl = baseUrl + "/api/v1/reports/export/" + job.getId() + "/download";
        }
        return new ReportJobResponse(job.getId(), job.getJobType(), job.getStatus(), job.getFormat(), downloadUrl, job.getErrorMessage(), job.getExpiresAt(), job.getCreatedAt(), job.getCompletedAt());
    }
}
