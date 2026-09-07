package com.tewendelabs.airag.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.tewendelabs.airag.repository.ChunkSearchResult;


@Component
public class CitationBuilder {

    public List<CitedContext> build(List<ChunkSearchResult> results) {
        Map<GroupKey, List<ChunkSearchResult>> groups = new LinkedHashMap<>();
        for (ChunkSearchResult result : results) {
            groups.computeIfAbsent(new GroupKey(result.documentId(), result.pageNumber()), k -> new ArrayList<>())
                    .add(result);
        }

        List<CitedContext> citedContexts = new ArrayList<>();
        int referenceNumber = 1;
        for (List<ChunkSearchResult> group : groups.values()) {
            ChunkSearchResult first = group.get(0);
            String content = group.stream()
                    .sorted(Comparator.comparingInt(ChunkSearchResult::chunkIndex))
                    .map(ChunkSearchResult::content)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
            double maxSimilarity = group.stream().mapToDouble(ChunkSearchResult::similarity).max().orElse(0.0);

            citedContexts.add(new CitedContext(referenceNumber++, first.documentTitle(), first.filename(),
                    first.pageNumber(), first.sectionLabel(), maxSimilarity, content));
        }
        return citedContexts;
    }

    private record GroupKey(UUID documentId, Integer pageNumber) {
    }
}
