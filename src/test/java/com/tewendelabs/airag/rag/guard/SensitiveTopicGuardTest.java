package com.tewendelabs.airag.rag.guard;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SensitiveTopicGuardTest {

    private final SensitiveTopicGuard guard = new SensitiveTopicGuard();

    @Test
    void blocksQuestionAboutNamedIndividualSalary() {
        assertThatThrownBy(() -> guard.check("Quel est le salaire de Jean Dupont ?"))
                .isInstanceOf(RagRefusalException.class)
                .extracting(ex -> ((RagRefusalException) ex).getReason())
                .isEqualTo(RefusalReason.SENSITIVE_TOPIC);
    }

    @Test
    void blocksQuestionAboutNamedIndividualHealth() {
        assertThatThrownBy(() -> guard.check("Marie Curie est-elle en arret maladie ?"))
                .isInstanceOf(RagRefusalException.class);
    }

    @Test
    void allowsGeneralPolicyQuestionMentioningTheSameVocabulary() {
        assertThatCode(() -> guard.check("Quelle est la politique de conges maladie de l'entreprise ?"))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsGeneralCompensationQuestionWithoutNamedIndividual() {
        assertThatCode(() -> guard.check("Quelle est la politique de remuneration pour les nouveaux employes ?"))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsNullQuestion() {
        assertThatCode(() -> guard.check(null)).doesNotThrowAnyException();
    }

    @Test
    void allowsQuestionWithNamedIndividualButNoSensitiveAttribute() {
        assertThatCode(() -> guard.check("Ou puis-je joindre Jean Dupont pour une question technique ?"))
                .doesNotThrowAnyException();
    }
}
