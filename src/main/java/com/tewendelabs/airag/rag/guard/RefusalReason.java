package com.tewendelabs.airag.rag.guard;

public enum RefusalReason {
    SENSITIVE_TOPIC,
    DEPARTMENT_FORBIDDEN,
    NO_RELEVANT_CONTEXT,
    QUOTA_EXCEEDED,
    SESSION_EXPIRED
}
