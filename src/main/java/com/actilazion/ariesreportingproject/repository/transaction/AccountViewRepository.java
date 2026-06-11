package com.actilazion.ariesreportingproject.repository.transaction;

import com.actilazion.ariesreportingproject.entity.transaction.AccountView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@Transactional(
        transactionManager = "transactionTransactionManager",
        readOnly = true
)
public interface AccountViewRepository extends JpaRepository<AccountView, UUID> {
}
