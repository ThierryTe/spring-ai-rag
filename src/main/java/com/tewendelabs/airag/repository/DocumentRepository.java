package com.tewendelabs.airag.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tewendelabs.airag.entity.Document;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByDepartmentIdIn(List<Integer> departmentIds);

    List<Document> findByDemoSessionId(UUID demoSessionId);
}
