package com.tewendelabs.airag.ingestion;

import com.tewendelabs.airag.exceptions.FileValidationException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tewendelabs.airag.config.IngestionProperties;

@Service
public class FileValidationService {

    private static final int MAX_FILENAME_LENGTH = 255;

    private final IngestionProperties properties;
    private final Tika tika = new Tika();

    public FileValidationService(IngestionProperties properties) {
        this.properties = properties;
    }

    /** Retourne le type MIME reel detecte si le fichier est valide, leve sinon. */
    public String validate(MultipartFile file, byte[] content) {
        if (content.length == 0) {
            throw new FileValidationException("Le fichier est vide");
        }
        long maxBytes = properties.maxFileSizeMb() * 1024L * 1024L;
        if (content.length > maxBytes) {
            throw new FileValidationException(
                    "Le fichier depasse la taille maximale autorisee (" + properties.maxFileSizeMb() + " Mo)");
        }

        validateFilename(file.getOriginalFilename());

        String detectedType = tika.detect(content, file.getOriginalFilename());
        if (!properties.allowedContentTypes().contains(detectedType)) {
            throw new FileValidationException("Type de fichier non autorise : " + detectedType);
        }
        return detectedType;
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new FileValidationException("Nom de fichier manquant");
        }
        if (filename.length() > MAX_FILENAME_LENGTH) {
            throw new FileValidationException("Nom de fichier trop long");
        }
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")
                || filename.indexOf('\0') >= 0) {
            throw new FileValidationException("Nom de fichier invalide");
        }
    }
}
