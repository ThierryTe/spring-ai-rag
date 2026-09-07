package com.tewendelabs.airag.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Test unitaire direct (pas de contexte Spring) des points d'extension redefinis sur
 * {@link GlobalExceptionHandler} : {@code MaxUploadSizeExceededException} n'est pas fiable a
 * declencher via MockMvc (la verification de taille depend du parsing multipart du conteneur
 * servlet reel, que MockMvc ne simule pas forcement) - on invoque donc directement le hook, qui
 * est {@code protected} mais visible ici puisque ce test est dans le meme package.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void maxUploadSizeExceeded_returnsCustomFrenchMessageNotSpringDefault() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(25L * 1024 * 1024);
        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("POST", "/api/documents"));

        ResponseEntity<Object> response = handler.handleMaxUploadSizeExceededException(ex, new HttpHeaders(),
                HttpStatus.PAYLOAD_TOO_LARGE, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo("Le fichier depasse la taille maximale autorisee");
        assertThat(body.path()).isEqualTo("/api/documents");
    }

    @Test
    void handleExceptionInternal_hidesInternalDetailsWhenStatusIsServerError() {
        RuntimeException ex = new RuntimeException("detail interne sensible");
        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("GET", "/api/chat"));

        ResponseEntity<Object> response = handler.handleExceptionInternal(ex, null, new HttpHeaders(),
                HttpStatus.INTERNAL_SERVER_ERROR, request);

        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo("Une erreur inattendue est survenue");
        assertThat(body.message()).doesNotContain("detail interne sensible");
    }

    @Test
    void handleExceptionInternal_keepsExceptionMessageForNonServerErrorStatus() {
        RuntimeException ex = new RuntimeException("parametre manquant");
        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("GET", "/api/documents"));

        ResponseEntity<Object> response = handler.handleExceptionInternal(ex, null, new HttpHeaders(),
                HttpStatus.BAD_REQUEST, request);

        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo("parametre manquant");
    }
}
