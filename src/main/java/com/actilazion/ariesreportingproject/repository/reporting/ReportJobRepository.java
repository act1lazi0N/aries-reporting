package com.actilazion.ariesreportingproject.repository.reporting;

import com.actilazion.ariesreportingproject.entity.reporting.ReportJob;
import com.actilazion.ariesreportingproject.enums.ReportJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ReportJobRepository extends JpaRepository<ReportJob, UUID> {
    // Retrieves report jobs requested by a user, ordered newest first with pagination.
    Page<ReportJob> findAllByRequestedByOrderByCreatedAtDesc(
            UUID requestedBy, Pageable pageable);

    // Retrieves report jobs with the specified status that expired before the given time.
    List<ReportJob> findAllByStatusAndExpiresAtBefore(
            ReportJobStatus status, OffsetDateTime now);

    // Retrieves report jobs whose status is in the given list and were created before the threshold.
    List<ReportJob> findAllByStatusInAndCreatedAtBefore(
            List<ReportJobStatus> statuses, OffsetDateTime threshold);

}
