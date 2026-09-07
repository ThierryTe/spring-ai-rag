package com.tewendelabs.airag.rag;


public record CitedContext(
        int referenceNumber,
        String documentTitle,
        String filename,
        Integer pageNumber,
        String sectionLabel,
        double similarityScore,
        String content) {
}
