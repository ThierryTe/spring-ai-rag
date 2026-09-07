package com.tewendelabs.airag.exceptions;

public record ErrorResponse(int status, String error, String message, String path) {
}
