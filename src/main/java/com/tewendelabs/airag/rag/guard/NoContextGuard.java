package com.tewendelabs.airag.rag.guard;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tewendelabs.airag.repository.ChunkSearchResult;


@Component
public class NoContextGuard {

    public void check(List<ChunkSearchResult> results, double minSimilarity) {
        if (results.isEmpty() || results.get(0).similarity() < minSimilarity) {
            throw new RagRefusalException(RefusalReason.NO_RELEVANT_CONTEXT);
        }
    }
}
