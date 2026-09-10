package com.tewendelabs.airag.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex,
            HttpServletRequest req) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenOperationException ex, HttpServletRequest req) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), req.getRequestURI());
    }

    /**
     * AuthorizationDeniedException (@PreAuthorize) etend cette classe. Sans ce handler explicite,
     * elle serait interceptee par {@code handleUnexpected} (ExceptionHandlerExceptionResolver de
     * DispatcherServlet, avant l'accessDeniedHandler de SecurityConfig) et renverrait 500.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex, HttpServletRequest req) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied", req.getRequestURI());
    }

    @ExceptionHandler(FileValidationException.class)
    public ResponseEntity<ErrorResponse> handleFileValidation(FileValidationException ex, HttpServletRequest req) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(InvalidChatRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidChatRequest(InvalidChatRequestException ex,
            HttpServletRequest req) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(InvalidDemoSessionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDemoSession(InvalidDemoSessionException ex,
            HttpServletRequest req) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(DemoQuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleDemoQuotaExceeded(DemoQuotaExceededException ex,
            HttpServletRequest req) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Erreur inattendue sur {}", req.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur inattendue est survenue",
                req.getRequestURI());
    }

    /**
     * {@code ResponseEntityExceptionHandler.handleException} est lui-meme deja annote
     * {@code @ExceptionHandler} pour {@code MaxUploadSizeExceededException} (entre autres
     * exceptions Spring MVC connues) : y ajouter notre propre {@code @ExceptionHandler} pour ce
     * meme type serait une mapping ambigue au demarrage ("Ambiguous @ExceptionHandler method").
     * Le point d'extension prevu par la classe parente pour personnaliser CE cas precis est ce
     * hook protege, appele en interne par {@code handleException} avant d'atteindre
     * {@link #handleExceptionInternal}.
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, "Le fichier depasse la taille maximale autorisee", headers,
                HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Point d'arrivee commun de {@code ResponseEntityExceptionHandler} pour toutes ses exceptions
     * connues (JSON malforme, parametre manquant, upload trop volumineux, 404 de routing...) ET
     * pour toute exception totalement imprevue non couverte par un handler ci-dessus -
     * {@code statusCode} vaut alors 500 (fixe par {@code handleException} avant d'atteindre ce
     * point). {@code body}, quand fourni par un appelant interne (ex. le hook ci-dessus avec un
     * message de substitution), prime sur {@code ex.getMessage()}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String path = request instanceof ServletWebRequest servletWebRequest
                ? servletWebRequest.getRequest().getRequestURI()
                : "";
        String message;
        if (body instanceof String customMessage) {
            message = customMessage;
        } else if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            message = "Une erreur inattendue est survenue";
        } else {
            message = ex.getMessage();
        }
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            log.error("Erreur inattendue sur {}", path, ex);
        }
        return new ResponseEntity<>(
                new ErrorResponse(status.value(), status.getReasonPhrase(), message, path), headers, statusCode);
    }

    private static ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, String path) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), status.getReasonPhrase(),
                message, path));
    }
}
