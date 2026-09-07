package com.tewendelabs.airag.rag.guard;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;


@Component
public class SensitiveTopicGuard {

    private static final Pattern SENSITIVE_ATTRIBUTE = Pattern.compile(
            "(?i)\\b(salaire|remuneration|augmentation salariale|prime individuelle|"
                    + "sante|maladie|arret maladie|arret de travail|dossier medical|handicap|"
                    + "grossesse|conge parental|sanction disciplinaire|licenciement|mise a pied|"
                    + "orientation sexuelle|religion|casier judiciaire|origine ethnique|"
                    + "evaluation de performance|entretien annuel)\\b");

    private static final Pattern NAMED_INDIVIDUAL = Pattern.compile(
            "\\b\\p{Lu}[\\p{L}'-]+\\s+\\p{Lu}[\\p{L}'-]+\\b");

    public void check(String question) {
        if (question == null) {
            return;
        }
        String normalized = java.text.Normalizer.normalize(question, java.text.Normalizer.Form.NFC);
        if (SENSITIVE_ATTRIBUTE.matcher(normalized).find() && NAMED_INDIVIDUAL.matcher(normalized).find()) {
            throw new RagRefusalException(RefusalReason.SENSITIVE_TOPIC);
        }
    }
}
