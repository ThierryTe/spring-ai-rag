package com.tewendelabs.airag.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "demo_session_id")
    private UUID demoSessionId;

    @Column(nullable = false)
    private String filename;

    private String title;

    @Column(name = "ingested_at", nullable = false)
    private LocalDateTime ingestedAt;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @PrePersist
    void onPrePersist() {
        if (ingestedAt == null) {
            ingestedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = DocumentStatus.UPLOADED;
        }
        if (version == 0) {
            version = 1;
        }
    }
}
