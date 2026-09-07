package com.tewendelabs.airag.rag.guard;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tewendelabs.airag.entity.User;
import com.tewendelabs.airag.service.AccessScopeResolver;


@Component
public class DepartmentAccessGuard {

    private final AccessScopeResolver accessScopeResolver;

    public DepartmentAccessGuard(AccessScopeResolver accessScopeResolver) {
        this.accessScopeResolver = accessScopeResolver;
    }

    public List<Integer> resolveScope(User user, Integer requestedDepartmentId) {
        List<Integer> authorized = accessScopeResolver.resolveAuthorizedDepartmentIds(user);
        if (requestedDepartmentId == null) {
            if (authorized.isEmpty()) {
                throw new RagRefusalException(RefusalReason.DEPARTMENT_FORBIDDEN);
            }
            return authorized;
        }
        if (!authorized.contains(requestedDepartmentId)) {
            throw new RagRefusalException(RefusalReason.DEPARTMENT_FORBIDDEN);
        }
        return List.of(requestedDepartmentId);
    }
}
