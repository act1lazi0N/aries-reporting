package com.actilazion.ariesreportingproject.repository.reporting;

import com.actilazion.ariesreportingproject.entity.reporting.EmailLog;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, UUID> {
    // Checks whether an email log already exists by idempotency key.
    boolean existsByIdempotencyKey(String idempotencyKey);

    // Finds an email log by idempotency key.
    Optional<EmailLog> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EmailLog e WHERE e.idempotencyKey = :idempotencyKey")
    Optional<EmailLog> findByIdempotencyKeyForUpdate(String idempotencyKey);

    // Retrieves email logs for a user, ordered by sent time descending.
    List<EmailLog> findAllByUserIdOrderBySentAtDesc(UUID userId);
}
