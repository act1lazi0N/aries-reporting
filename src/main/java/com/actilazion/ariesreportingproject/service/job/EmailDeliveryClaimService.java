package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.entity.reporting.EmailLog;
import com.actilazion.ariesreportingproject.enums.EmailStatus;
import com.actilazion.ariesreportingproject.repository.reporting.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailDeliveryClaimService {
    private final EmailLogRepository emailLogRepository;

    @Transactional(transactionManager = "reportingTransactionManager")
    public Optional<EmailLog> claim(UUID userId, String billingMonth, String idempotencyKey) {
        Optional<EmailLog> existing = emailLogRepository.findByIdempotencyKeyForUpdate(idempotencyKey);
        if (existing.isPresent()) {
            EmailLog log = existing.get();
            if (log.getStatus() == EmailStatus.SENT || log.getStatus() == EmailStatus.SENDING) {
                return Optional.empty();
            }
            log.setStatus(EmailStatus.SENDING);
            log.setErrorMessage(null);
            return Optional.of(emailLogRepository.saveAndFlush(log));
        }

        try {
            EmailLog log = EmailLog.builder()
                    .userId(userId)
                    .billingMonth(billingMonth)
                    .idempotencyKey(idempotencyKey)
                    .status(EmailStatus.SENDING)
                    .build();
            return Optional.of(emailLogRepository.saveAndFlush(log));
        } catch (DataIntegrityViolationException duplicateClaim) {
            return Optional.empty();
        }
    }

    @Transactional(transactionManager = "reportingTransactionManager")
    public void complete(UUID emailLogId, EmailStatus status, String errorReason) {
        EmailLog log = emailLogRepository.findById(emailLogId)
                .orElseThrow(() -> new IllegalStateException("Email log claim not found"));
        log.setStatus(status);
        log.setErrorMessage(errorReason);
        emailLogRepository.save(log);
    }
}
