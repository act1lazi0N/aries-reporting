package com.actilazion.ariesreportingproject.repository.transaction;

import com.actilazion.ariesreportingproject.entity.transaction.UserView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(
        transactionManager = "transactionTransactionManager",
        readOnly = true
)
public interface UserViewRepository extends JpaRepository<UserView, UUID> {
    Optional<UserView> findByEmail(String email);
}
