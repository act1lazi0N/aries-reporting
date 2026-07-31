package com.actilazion.ariesreportingproject.repository.transaction;

import com.actilazion.ariesreportingproject.entity.transaction.UserView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
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

    @Query("select u.id from UserView u where u.isActive = true")
    Slice<UUID> findActiveUserIds(Pageable pageable);
}
