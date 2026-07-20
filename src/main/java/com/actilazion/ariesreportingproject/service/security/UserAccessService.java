package com.actilazion.ariesreportingproject.service.security;

import com.actilazion.ariesreportingproject.entity.transaction.UserView;
import com.actilazion.ariesreportingproject.repository.transaction.AccountViewRepository;
import com.actilazion.ariesreportingproject.repository.transaction.UserViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAccessService {
    private final UserViewRepository userViewRepository;
    private final AccountViewRepository accountViewRepository;

    @Transactional(transactionManager = "transactionTransactionManager", readOnly = true)
    public UUID requireCurrentUserId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return userViewRepository.findByEmail(userDetails.getUsername())
                .map(UserView::getId)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));
    }

    @Transactional(transactionManager = "transactionTransactionManager", readOnly = true)
    public void requireAccountAccess(UserDetails userDetails, UUID accountId) {
        if (hasRole(userDetails, "ADMIN") || hasRole(userDetails, "AUDITOR")) {
            return;
        }
        UUID userId = requireCurrentUserId(userDetails);
        if (!accountViewRepository.existsByIdAndUserId(accountId, userId)) {
            throw new AccessDeniedException("Access denied to account");
        }
    }

    public boolean isAdmin(UserDetails userDetails) {
        return hasRole(userDetails, "ADMIN");
    }

    private boolean hasRole(UserDetails userDetails, String role) {
        if (userDetails == null) {
            return false;
        }
        String authority = "ROLE_" + role;
        return userDetails.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
