package com.tewendelabs.airag.rag.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tewendelabs.airag.entity.User;
import com.tewendelabs.airag.service.AccessScopeResolver;

class DepartmentAccessGuardTest {

    private final AccessScopeResolver accessScopeResolver = mock(AccessScopeResolver.class);
    private final DepartmentAccessGuard guard = new DepartmentAccessGuard(accessScopeResolver);
    private final User user = new User();

    @BeforeEach
    void stubAuthorizedDepartments() {
        when(accessScopeResolver.resolveAuthorizedDepartmentIds(user)).thenReturn(List.of(1, 2));
    }

    @Test
    void noDepartmentRequested_returnsFullAuthorizedScope() {
        assertThat(guard.resolveScope(user, null)).containsExactly(1, 2);
    }

    @Test
    void requestedDepartmentWithinScope_returnsSingletonScope() {
        assertThat(guard.resolveScope(user, 2)).containsExactly(2);
    }

    @Test
    void requestedDepartmentOutsideScope_refuses() {
        assertThatThrownBy(() -> guard.resolveScope(user, 99))
                .isInstanceOf(RagRefusalException.class)
                .extracting(ex -> ((RagRefusalException) ex).getReason())
                .isEqualTo(RefusalReason.DEPARTMENT_FORBIDDEN);
    }

    @Test
    void userWithNoAuthorizedDepartments_refusesEvenWithoutExplicitRequest() {
        when(accessScopeResolver.resolveAuthorizedDepartmentIds(user)).thenReturn(List.of());

        assertThatThrownBy(() -> guard.resolveScope(user, null))
                .isInstanceOf(RagRefusalException.class)
                .extracting(ex -> ((RagRefusalException) ex).getReason())
                .isEqualTo(RefusalReason.DEPARTMENT_FORBIDDEN);
    }
}
