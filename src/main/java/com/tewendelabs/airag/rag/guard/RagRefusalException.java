package com.tewendelabs.airag.rag.guard;

import lombok.Getter;

@Getter
public class RagRefusalException extends RuntimeException {

    private final RefusalReason reason;

    public RagRefusalException(RefusalReason reason) {
        super(reason.name());
        this.reason = reason;
    }
}
