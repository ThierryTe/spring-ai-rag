package com.tewendelabs.airag.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tewendelabs.airag.entity.Department;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Integer> {

    Optional<Department> findByCode(String code);

    @Query(value = "SELECT department_id FROM role_permissions WHERE role_id = :roleId", nativeQuery = true)
    List<Integer> findAllowedDepartmentIds(@Param("roleId") Integer roleId);
}
