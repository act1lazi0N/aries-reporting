package com.actilazion.ariesreportingproject.service.sync;

import com.actilazion.ariesreportingproject.entity.reporting.ReportingTransaction;
import com.actilazion.ariesreportingproject.entity.transaction.AccountView;
import com.actilazion.ariesreportingproject.entity.transaction.TransactionView;
import com.actilazion.ariesreportingproject.entity.transaction.UserView;
import com.actilazion.ariesreportingproject.repository.reporting.ReportingTransactionRepository;
import com.actilazion.ariesreportingproject.repository.transaction.AccountViewRepository;
import com.actilazion.ariesreportingproject.repository.transaction.TransactionViewRepository;
import com.actilazion.ariesreportingproject.repository.transaction.UserViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackfillService {
    private static final int BATCH_SIZE = 100;

    private final TransactionViewRepository transactionViewRepository;
    private final AccountViewRepository accountViewRepository;
    private final UserViewRepository userViewRepository;
    private final ReportingTransactionRepository reportingTransactionRepository;

    public long backfill(OffsetDateTime since) {
        log.info("[BACKFILL] Starting backfill from {}", since);
        long totalSynced = 0;
        int page = 0;

        Page<TransactionView> batch;
        do {
            batch = transactionViewRepository.findAllByCreatedAtAfterOrderByCreatedAtAsc(
                    since, PageRequest.of(page, BATCH_SIZE));

            long synced = processBatch(batch.getContent());
            totalSynced += synced;
            page++;

            log.info("[BACKFILL] Processed page={} synced={} total={}",
                    page, synced, totalSynced);

        } while (batch.hasNext());

        log.info("[BACKFILL] Completed. Total synced={}", totalSynced);
        return totalSynced;
    }

    protected long processBatch(List<TransactionView> transactionViews) {
        if (transactionViews.isEmpty()) {
            return 0;
        }

        List<UUID> accountIds = transactionViews.stream()
                .flatMap(tx -> List.of(tx.getFromAccountId(), tx.getToAccountId()).stream())
                .distinct()
                .toList();

        Map<UUID, AccountView> accountMap = accountViewRepository
                .findAllById(accountIds).stream()
                .collect(Collectors.toMap(AccountView::getId, a -> a));

        Map<UUID, UserView> userMap = accountIds.stream()
                .map(accountMap::get)
                .filter(a -> a != null)
                .map(AccountView::getUserId)
                .distinct()
                .map(userId -> userViewRepository.findById(userId).orElse(null))
                .filter(u -> u != null)
                .collect(Collectors.toMap(UserView::getId, u -> u));

        List<ReportingTransaction> toInsert = new ArrayList<>();

        for (TransactionView tx : transactionViews) {
            if (reportingTransactionRepository.existsByOriginalTxId(tx.getId())) {
                continue;
            }

            AccountView fromAccount = accountMap.get(tx.getFromAccountId());
            AccountView toAccount = accountMap.get(tx.getToAccountId());

            if (fromAccount == null || toAccount == null) {
                log.warn("[BACKFILL] Missing account for transactionId {}", tx.getId());
                continue;
            }

            UserView fromUser = userMap.get(fromAccount.getUserId());
            UserView toUser = userMap.get(toAccount.getUserId());

            short dayOfWeek = (short) tx.getCreatedAt().getDayOfWeek().getValue();
            short hourOfDay = (short) tx.getCreatedAt().getHour();

            toInsert.add(ReportingTransaction.builder()
                    .originalTxId(tx.getId())
                    .fromAccountId(tx.getFromAccountId())
                    .toAccountId(tx.getToAccountId())
                    .fromOwnerName(fromUser != null ? fromUser.getFullName() : "Unknown")
                    .toOwnerName(toUser != null ? toUser.getFullName() : "Unknown")
                    .fromAccountNumber(fromAccount.getAccountNumber())
                    .toAccountNumber(toAccount.getAccountNumber())
                    .amount(tx.getAmount())
                    .currency(tx.getCurrency())
                    .status(tx.getStatus())
                    .description(tx.getDescription())
                    .dayOfWeek(dayOfWeek)
                    .hourOfDay(hourOfDay)
                    .createdAt(tx.getCreatedAt())
                    .completedAt(tx.getCompletedAt())
                    .build());
        }

        reportingTransactionRepository.saveAll(toInsert);
        return toInsert.size();
    }
}
