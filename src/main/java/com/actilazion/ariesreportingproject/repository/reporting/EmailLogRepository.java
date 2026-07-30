package com.actilazion.ariesreportingproject.repository.reporting;

import com.actilazion.ariesreportingproject.entity.reporting.EmailLog;
import com.actilazion.ariesreportingproject.enums.EmailStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO email_logs (user_id, billing_month, idempotency_key, status, next_attempt_at)
            VALUES (:userId, :billingMonth, :idempotencyKey, 'PENDING'::email_status, NOW())
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertPending(@Param("userId") UUID userId,
                      @Param("billingMonth") String billingMonth,
                      @Param("idempotencyKey") String idempotencyKey);

    // Checks whether an email log already exists by idempotency key.
    boolean existsByIdempotencyKey(String idempotencyKey);

    // Finds an email log by idempotency key.
    Optional<EmailLog> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EmailLog e WHERE e.idempotencyKey = :idempotencyKey")
    Optional<EmailLog> findByIdempotencyKeyForUpdate(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e FROM EmailLog e
            WHERE e.id = :emailLogId
              AND e.status = :status
              AND e.claimToken = :claimToken
            """)
    Optional<EmailLog> findClaimedForUpdate(@Param("emailLogId") UUID emailLogId,
                                            @Param("status") EmailStatus status,
                                            @Param("claimToken") UUID claimToken);

    @Query(value = """
            SELECT *
            FROM email_logs
            WHERE (status = 'PENDING' AND next_attempt_at <= :now)
               OR (status = 'SENDING' AND lease_until <= :now)
            ORDER BY created_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<EmailLog> findDueForUpdate(@Param("now") java.time.OffsetDateTime now,
                                    @Param("limit") int limit);

    // Retrieves email logs for a user, ordered by sent time descending.
    List<EmailLog> findAllByUserIdOrderBySentAtDesc(UUID userId);
}
