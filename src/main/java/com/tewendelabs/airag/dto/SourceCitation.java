package com.tewendelabs.airag.dto;

public record SourceCitation(
        int referenceNumber,
        String documentTitle,
        String filename,
        Integer pageNumber,
        String sectionLabel,
        double similarityScore) {
}
