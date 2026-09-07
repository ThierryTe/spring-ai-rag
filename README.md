# ai-assistant-backend

Backend Spring Boot de l'Enterprise AI Knowledge Assistant - POC independant inspire de besoins
d'IA conversationnelle observes sur un projet public, sans donnees ni systemes confidentiels reels.

## Etat actuel de ce scaffold

Ce qui est implemente et teste :
- Schema de base de donnees complet (migration Flyway `V1__init_schema.sql`, `V2__seed_reference_data.sql`)
- Entites JPA de base : `Department`, `Role`, `User`
- **`AccessControlService`** : orchestrateur des deux gardes-fous de securite (sujet sensible, puis departement autorise), tous deux en code deterministe, appeles avant tout acces LLM
- **`SensitiveTopicGuard`** : detection Tier 1 des sujets RH individuels sensibles, avec tests unitaires couvrant les questions pieges S01-S04 du jeu de test
- **Package `rag/`** : `EmbeddingService` (OpenAI text-embedding-3-small), `ChunkSearchRepository` (recherche pgvector filtree par departement au niveau SQL), `RagOrchestrator` (recherche + generation, avec court-circuit si aucun chunk trouve pour eviter l'hallucination). Prompt systeme externalise dans `resources/prompts/rag-system-prompt.st`
- `QueryController` : point d'entree REST branchant l'ensemble du flux (acces -> RAG -> reponse)

Ce qui reste a implementer (hors scope de ce scaffold) :
- Package `ingestion/` : parsing des documents (Tika), chunking, generation et stockage des embeddings - necessaire pour peupler la table `chunks` avant que la recherche RAG ne retourne quoi que ce soit
- Package `security/` (JWT) : authentification, extraction du `userId` depuis le token (le controller suppose actuellement un `userId` deja pose en attribut de requete par un filtre a ecrire)
- Mode demo anonyme : gestion de `demo_sessions`, quotas, job de nettoyage planifie
- `query_logs` : le logging de chaque requete (latence, tokens, cout) n'est pas encore branche dans `RagOrchestrator`
- Package `eval/` : harness d'evaluation rejouant le jeu de questions de test contre l'API

## Lancer en local

```bash
# Necessite Docker pour Postgres + pgvector, et une cle OPENAI_API_KEY
docker run -d --name aiassistant-db -e POSTGRES_DB=aiassistant \
  -e POSTGRES_USER=aiassistant -e POSTGRES_PASSWORD=changeme \
  -p 5432:5432 pgvector/pgvector:pg16

export OPENAI_API_KEY=sk-...
mvn spring-boot:run
```

## Tests

```bash
mvn test
```

Le test `SensitiveTopicGuardTest` reprend directement les questions du jeu de test de conception
(S01-S04, plus un echantillon de Q01-Q04 pour verifier l'absence de faux positifs).
