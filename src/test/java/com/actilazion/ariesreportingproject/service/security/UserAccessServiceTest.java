package com.actilazion.ariesreportingproject.service.security;

import com.actilazion.ariesreportingproject.entity.transaction.UserView;
import com.actilazion.ariesreportingproject.repository.transaction.AccountViewRepository;
import com.actilazion.ariesreportingproject.repository.transaction.UserViewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccessServiceTest {
    @Mock
    UserViewRepository userViewRepository;
    @Mock
    AccountViewRepository accountViewRepository;

    @Test
    @DisplayName("requireAccountAccess: admin skips account ownership lookup")
    void requireAccountAccess_adminSkipsOwnershipLookup() {
        UserAccessService service = new UserAccessService(userViewRepository, accountViewRepository);
        var admin = User.withUsername("admin@aries.local")
                .password("n/a")
                .roles("ADMIN")
                .build();
        UUID accountId = UUID.randomUUID();

        service.requireAccountAccess(admin, accountId);

        verifyNoInteractions(accountViewRepository, userViewRepository);
    }

    @Test
    @DisplayName("requireAccountAccess: user must own account")
    void requireAccountAccess_userMustOwnAccount() {
        UserAccessService service = new UserAccessService(userViewRepository, accountViewRepository);
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        var userDetails = User.withUsername("user@aries.local")
                .password("n/a")
                .roles("USER")
                .build();
        when(userViewRepository.findByEmail("user@aries.local"))
                .thenReturn(Optional.of(userView(userId)));
        when(accountViewRepository.existsByIdAndUserId(accountId, userId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.requireAccountAccess(userDetails, accountId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access denied to account");
    }

    @Test
    @DisplayName("requireCurrentUserId: rejects missing principal")
    void requireCurrentUserId_missingPrincipal_denied() {
        UserAccessService service = new UserAccessService(userViewRepository, accountViewRepository);

        assertThatThrownBy(() -> service.requireCurrentUserId(null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Authentication required");
    }

    private UserView userView(UUID userId) {
        UserView view = new UserView();
        ReflectionTestUtils.setField(view, "id", userId);
        ReflectionTestUtils.setField(view, "email", "user@aries.local");
        ReflectionTestUtils.setField(view, "role", "USER");
        ReflectionTestUtils.setField(view, "isActive", true);
        ReflectionTestUtils.setField(view, "fullName", "Test User");
        return view;
    }
}
