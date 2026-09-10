# ai-assistant-backend

Backend Spring Boot de l'Enterprise AI Knowledge Assistant - POC independant inspire de besoins
d'IA conversationnelle observes sur un projet public, sans donnees ni systemes confidentiels reels.

## Fonctionnalites

- **Authentification JWT** : login (`/api/auth/login`), extraction du `userId`/role depuis le
  token via `JwtAuthenticationFilter`, endpoint `/api/auth/me`
- **Ingestion de documents** (`ingestion/`) : upload PDF/DOCX/TXT, validation (taille, type,
  nombre de pages), extraction de texte (Apache Tika), nettoyage, chunking configurable,
  generation d'embeddings par lot (OpenAI `text-embedding-3-small`) et persistance en pgvector
- **RAG** (`rag/`) : recherche par similarite filtree par departement au niveau SQL
  (`ChunkSearchRepository`), construction du prompt avec citations (`PromptBuilder`,
  `CitationBuilder`), orchestration recherche + generation (`RagQueryService`)
- **Garde-fous anti-hallucination**, empiles avant tout appel LLM (`rag/guard/`) :
  - `SensitiveTopicGuard` - sujets RH individuels sensibles
  - `DepartmentAccessGuard` - perimetre departement autorise
  - `QuotaGuard` - quota de questions/jour par utilisateur
  - `NoContextGuard` - court-circuit si aucun chunk pertinent trouve, pour eviter l'hallucination
  - Chaque refus est typé (`RefusalReason`) et renvoyé en `200` (`refused=true`), pas en erreur HTTP
- **Mode demo anonyme** (`/api/demo`) : creation de session, upload limite de documents, chat
  limite, rate limiting par IP (Bucket4j), expiration et nettoyage planifie des sessions
  (`DemoSessionCleanupJob`)
- **Observabilite** : `query_logs` (latence, tokens, cout estime) alimenté à chaque requête RAG,
  dashboard admin (`/api/admin/observability/summary`, role `ADMIN`), metriques Prometheus
  (`/actuator/prometheus`)
- **Eval** (`eval/`) : `RagGoldenEvalTest`, harness qui rejoue un jeu de questions de test (dont
  les pieges S01-S04 sur les sujets sensibles) contre un vrai modele OpenAI ; exclu du build par
  defaut (groupe Maven `eval`), lance separement en CI
- **Documentation API** : OpenAPI/Swagger via springdoc (`/swagger-ui.html`)

## Endpoints principaux

| Methode | Route | Acces | Description |
|---|---|---|---|
| POST | `/api/auth/login` | public | Authentification, retourne un JWT |
| GET | `/api/auth/me` | authentifie | Profil de l'utilisateur courant |
| POST | `/api/chat` | authentifie | Question -> reponse citee (ou refus type) |
| POST | `/api/documents` | MANAGER, ADMIN | Upload d'un document (declenche l'ingestion) |
| GET | `/api/documents` | authentifie | Liste des documents |
| GET | `/api/documents/{id}` | authentifie | Detail d'un document |
| GET | `/api/documents/{id}/status` | authentifie | Statut d'ingestion |
| DELETE | `/api/documents/{id}` | MANAGER, ADMIN | Suppression d'un document |
| POST | `/api/demo/sessions` | public | Creation d'une session demo anonyme |
| GET | `/api/demo/sessions/me` | session demo | Etat de la session courante |
| POST | `/api/demo/documents` | session demo | Upload limite (mode demo) |
| GET | `/api/demo/documents` | session demo | Documents de la session demo |
| POST | `/api/demo/chat` | session demo | Chat limite (mode demo) |
| GET | `/api/admin/observability/summary` | ADMIN | Metriques d'usage (requetes, refus, cout) |

## Schema de base de donnees

Migrations Flyway (`src/main/resources/db/migration`) :
`V1` schema initial - `V2` donnees de reference - `V3` role admin - `V4` colonnes
ingestion/citation - `V5` cascade query_logs/demo_sessions - `V6` utilisateurs de demo.

## Lancer en local

```bash
# Necessite Docker pour Postgres + pgvector, et une cle OPENAI_API_KEY
docker compose up -d postgres

export OPENAI_API_KEY=sk-...
export JWT_SECRET=...                 # au moins 32 caracteres
export POSTGRES_DB_NAME=aiassistant
export POSTGRES_USER_NAME=aiassistant
export POSTGRES_PASSWORD=changeme
export APP_CORS_ALLOWED_ORIGINS=http://localhost:4200

mvn spring-boot:run
```

Un fichier `.env` local (charge via `DotEnConfig`) peut aussi porter ces variables plutot que de
les exporter manuellement.

`docker compose up -d` (sans argument) demarre en plus `pgadmin` sur `http://localhost:5050`.

## Tests

```bash
mvn test
```

Suite couvrant les guards RAG, l'ingestion, l'auth, les controllers (integration, Testcontainers
Postgres+pgvector coherent avec `compose.yaml`), et le schema (Flyway). L'eval dataset
(`RagGoldenEvalTest`, appel reel a OpenAI) est exclu par defaut :

```bash
mvn test -Dgroups=eval -DexcludedGroups=
```

## Deploiement

Pipeline CI (`.github/workflows/ci.yml`) : tests + analyse SonarQube sur push/PR vers `main`/
`develop`, job separe pour l'eval golden dataset.

Deploiement sur VPS (Docker, GHCR, `compose.prod.yaml`) via `.github/workflows/deploy.yml`
(declenchement manuel) - voir [`docs/DEPLOY.md`](docs/DEPLOY.md) pour la mise en place complete.
