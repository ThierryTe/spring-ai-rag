package com.tewendelabs.airag.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tewendelabs.airag.entity.User;
import com.tewendelabs.airag.repository.DepartmentRepository;


@Service
public class AccessScopeResolver {

    private final DepartmentRepository departmentRepository;

    public AccessScopeResolver(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public List<Integer> resolveAuthorizedDepartmentIds(User user) {
        return departmentRepository.findAllowedDepartmentIds(user.getRole().getId());
    }
}
