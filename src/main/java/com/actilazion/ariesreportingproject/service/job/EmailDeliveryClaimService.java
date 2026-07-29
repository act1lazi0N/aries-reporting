package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.config.EmailDeliveryProperties;
import com.actilazion.ariesreportingproject.entity.reporting.EmailLog;
import com.actilazion.ariesreportingproject.enums.EmailStatus;
import com.actilazion.ariesreportingproject.repository.reporting.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailDeliveryClaimService {
    private final EmailLogRepository emailLogRepository;
    private final EmailDeliveryProperties properties;
    private final Clock clock;

    @Transactional(transactionManager = "reportingTransactionManager")
    public void enqueue(UUID userId, String billingMonth, String idempotencyKey) {
        emailLogRepository.insertPending(userId, billingMonth, idempotencyKey);
    }

    @Transactional(transactionManager = "reportingTransactionManager")
    public List<EmailLog> claimDue(OffsetDateTime now) {
        List<EmailLog> due = emailLogRepository.findDueForUpdate(
                now, properties.getDispatchBatchSize());
        OffsetDateTime leaseUntil = now.plus(properties.getLeaseDuration());
        for (EmailLog delivery : due) {
            delivery.setStatus(EmailStatus.SENDING);
            delivery.setClaimToken(UUID.randomUUID());
            delivery.setLeaseUntil(leaseUntil);
            delivery.setErrorMessage(null);
        }
        emailLogRepository.flush();
        return due;
    }

    @Transactional(transactionManager = "reportingTransactionManager")
    public int incrementAttempt(UUID emailLogId, UUID claimToken) {
        EmailLog delivery = findClaimed(emailLogId, claimToken);
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        return emailLogRepository.saveAndFlush(delivery).getAttemptCount();
    }

    @Transactional(transactionManager = "reportingTransactionManager")
    public void complete(UUID emailLogId, UUID claimToken, EmailStatus status, String errorReason) {
        EmailLog delivery = findClaimed(emailLogId, claimToken);
        delivery.setStatus(status);
        delivery.setErrorMessage(errorReason);
        delivery.setLeaseUntil(null);
        if (status == EmailStatus.SENT) {
            delivery.setSentAt(OffsetDateTime.now(clock));
        }
        emailLogRepository.save(delivery);
    }

    @Transactional(transactionManager = "reportingTransactionManager")
    public void retry(UUID emailLogId, UUID claimToken, String errorReason, OffsetDateTime nextAttemptAt) {
        EmailLog delivery = findClaimed(emailLogId, claimToken);
        delivery.setStatus(EmailStatus.PENDING);
        delivery.setErrorMessage(errorReason);
        delivery.setLeaseUntil(null);
        delivery.setNextAttemptAt(nextAttemptAt);
        emailLogRepository.save(delivery);
    }

    private EmailLog findClaimed(UUID emailLogId, UUID claimToken) {
        return emailLogRepository.findClaimedForUpdate(emailLogId, EmailStatus.SENDING, claimToken)
                .orElseThrow(() -> new IllegalStateException("Email delivery claim is no longer active"));
    }
}
