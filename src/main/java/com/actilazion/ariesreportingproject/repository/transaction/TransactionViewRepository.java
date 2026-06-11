package com.actilazion.ariesreportingproject.repository.transaction;

import com.actilazion.ariesreportingproject.entity.transaction.TransactionView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only repository for {@link TransactionView}.
 * transactionManager = "transactionTransactionManager"
 * never calls save() or delete() from this repository.
 */
@Repository
@Transactional(
        transactionManager = "transactionTransactionManager",
        readOnly = true
)
public interface TransactionViewRepository extends JpaRepository<TransactionView, UUID> {
    Page<TransactionView> findAllByCreatedAtAfterOrderByCreatedAtAsc(
            OffsetDateTime after, Pageable pageable);

    long countByCreatedAtAfter(OffsetDateTime after);
}
