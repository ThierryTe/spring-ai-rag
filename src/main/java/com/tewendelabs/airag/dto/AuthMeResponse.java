package com.tewendelabs.airag.dto;

import java.util.List;
import java.util.UUID;

public record AuthMeResponse(
        UUID id,
        String email,
        String fullName,
        String roleCode,
        List<DepartmentSummary> allowedDepartments) {
}
