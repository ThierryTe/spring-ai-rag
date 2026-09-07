package com.tewendelabs.airag.ingestion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.tewendelabs.airag.exceptions.TextExtractionException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;


@Service
public class TextExtractionService {

    public ExtractedDocument extract(byte[] fileBytes) {
        Metadata metadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler(-1);
        AutoDetectParser parser = new AutoDetectParser();
        try (InputStream in = new ByteArrayInputStream(fileBytes)) {
            parser.parse(in, handler, metadata, new ParseContext());
        } catch (IOException | SAXException | TikaException e) {
            throw new TextExtractionException("Echec de l'extraction du texte du document", e);
        }
        return new ExtractedDocument(handler.toString(), parsePageCount(metadata.get(PagedText.N_PAGES)));
    }

    private Integer parsePageCount(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
