# AI Compliance Copilot

**Assistant documentaire GenAI avec Java 21, Spring Boot, Spring AI et PostgreSQL/pgVector.**

AI Compliance Copilot est un projet personnel de démonstration technique inspiré de problématiques métier issues d'un **appel d'offres publié au Burkina Faso**.

L'objectif est de concevoir une application de recherche documentaire intelligente permettant aux utilisateurs d'interroger des documents en langage naturel, tout en appliquant des règles strictes de contrôle d'accès et en assurant la traçabilité des réponses.

**Ce projet est indépendant de l'organisme à l'origine de l'appel d'offres et n'utilise aucune donnée ni aucun système confidentiel réel.**

### Liens

- **Démo publique (sans compte) :** https://ai-frontend.tewendelabs.com
- **Frontend Angular :** https://github.com/ThierryTe/spring-ai-rag-frontend
- **Backend Java / Spring AI :** https://github.com/ThierryTe/spring-ai-rag
- **Documentation API :** https://springairag.tewendelabs.com/swagger-ui/index.html

## Le problème métier

Dans un environnement d'entreprise, un assistant RAG ne doit pas simplement retrouver des documents pertinents.

Il doit également garantir que **chaque utilisateur ne peut consulter que les informations autorisées pour son rôle et son département**.

AI Compliance Copilot explore cette problématique à travers une architecture associant recherche vectorielle, contrôles d'accès, garde-fous applicatifs et évaluation automatisée de la qualité des réponses.

## Architecture technique

**Frontend :** Angular

**Backend :** Java 21, Spring Boot, Spring AI, Spring Security

**IA générative :** OpenAI pour les embeddings et la génération

**Données :** PostgreSQL, pgVector, Flyway

**Qualité :** JUnit, Testcontainers, SonarQube, golden dataset RAG

**GenAIOps :** GitHub Actions, Micrometer, Prometheus, Grafana

**Déploiement :** Docker, GHCR, VPS Contabo

### Pipeline d'ingestion documentaire

Upload → Validation → Extraction Apache Tika → Nettoyage → Chunking → Embeddings OpenAI → PostgreSQL/pgVector

L'ingestion accepte les documents PDF, DOCX et TXT, avec validation de la taille, du type et du nombre de pages.

Les embeddings sont générés par lots avec le modèle `text-embedding-3-small`.

### Pipeline RAG

Question → Contrôle des autorisations → Recherche vectorielle filtrée → Vérification du contexte → Construction du prompt → Génération OpenAI → Réponse avec citations

**Point clé : le filtrage par département est appliqué au niveau SQL dans `ChunkSearchRepository`, avant que les passages documentaires soient transmis au modèle.**

## Fonctionnalités

### Authentification et autorisations

- Authentification JWT avec Spring Security.
- Extraction de l'identité et du rôle via `JwtAuthenticationFilter`.
- Trois profils : **Employee, Manager, Admin**.
- Restrictions d'accès aux documents selon les rôles et départements.
- Quotas de questions par jour pour les utilisateurs authentifiés.

Les différents parcours Employee / Manager / Admin peuvent être testés avec des comptes de démonstration disponibles sur demande.

### RAG et citations

- Ingestion asynchrone de documents.
- Recherche sémantique avec PostgreSQL/pgVector.
- Filtrage documentaire par département directement dans la requête SQL.
- Construction de prompts contextualisés avec `PromptBuilder`.
- Génération de citations via `CitationBuilder`.
- Orchestration du pipeline avec `RagQueryService`.

### Garde-fous applicatifs

Les garde-fous sont regroupés dans `rag/guard/` :

| Garde-fou | Fonction |
|---|---|
| `SensitiveTopicGuard` | Détecter certains sujets RH individuels sensibles |
| `DepartmentAccessGuard` | Vérifier le périmètre départemental autorisé |
| `QuotaGuard` | Appliquer les quotas de questions |
| `NoContextGuard` | Refuser de générer une réponse sans contexte documentaire pertinent |

Les refus sont identifiés par un `RefusalReason` et renvoyés dans une réponse HTTP `200` avec `refused=true`.

Le `NoContextGuard` permet notamment de **court-circuiter le traitement avant la génération LLM** lorsqu'aucun passage pertinent n'est disponible.

### Démo anonyme

La démo publique permet de tester l'application sans créer de compte.

Elle comprend :

- La création de sessions temporaires.
- Un nombre limité de documents et de questions.
- Un rate limiting par IP avec Bucket4j.
- L'expiration et le nettoyage planifié des sessions avec `DemoSessionCleanupJob`.

### GenAIOps et observabilité

Chaque requête RAG alimente les journaux d'usage (`query_logs`) pour suivre notamment :

- La latence.
- La consommation de tokens.
- Le coût estimé des appels au modèle.
- Les refus de réponse.

Un tableau de bord administrateur est disponible via `/api/admin/observability/summary`.

Les métriques applicatives sont également exposées via `/actuator/prometheus` pour leur exploitation dans Prometheus et Grafana.

### Évaluation RAG et Quality Gate

Le projet comprend un harness d'évaluation `RagGoldenEvalTest`, situé dans le module `eval/`.

Il exécute un ensemble de questions de référence contre un véritable modèle OpenAI, notamment des cas sensibles identifiés `S01` à `S04`.

Cette évaluation :

- Est séparée des tests standards.
- Utilise le groupe Maven `eval`.
- Est exécutée dans un job GitHub Actions dédié.
- Participe au **GenAI Quality Gate** du pipeline CI.

Le déploiement sur Contabo reste volontairement **manuel**, adapté au contexte d'une application de démonstration.

## API — principaux endpoints

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| POST | `/api/auth/login` | Public | Authentification JWT |
| GET | `/api/auth/me` | Authentifié | Profil courant |
| POST | `/api/chat` | Authentifié | Question RAG et réponse sourcée ou refus |
| POST | `/api/documents` | Manager / Admin | Import documentaire |
| GET | `/api/documents` | Authentifié | Liste des documents |
| GET | `/api/documents/{id}` | Authentifié | Détails d'un document |
| GET | `/api/documents/{id}/status` | Authentifié | Statut d'ingestion |
| DELETE | `/api/documents/{id}` | Manager / Admin | Suppression |
| POST | `/api/demo/sessions` | Public | Création d'une session anonyme |
| GET | `/api/demo/sessions/me` | Session démo | État de la session |
| POST | `/api/demo/documents` | Session démo | Import limité |
| GET | `/api/demo/documents` | Session démo | Documents de la session |
| POST | `/api/demo/chat` | Session démo | Questions RAG limitées |
| GET | `/api/admin/observability/summary` | Admin | Métriques d'utilisation |

La documentation OpenAPI/Swagger est disponible via `/swagger-ui.html`.

## Base de données

Le schéma est versionné avec Flyway dans `src/main/resources/db/migration`.

| Migration | Description |
|---|---|
| `V1` | Schéma initial |
| `V2` | Données de référence |
| `V3` | Rôle administrateur |
| `V4` | Colonnes d'ingestion et de citation |
| `V5` | Cascades sur `query_logs` et `demo_sessions` |
| `V6` | Utilisateurs de démonstration |

## Démarrage local

**Prérequis :** Java 21, Maven, Docker et une clé API OpenAI.

Démarrer PostgreSQL avec pgVector :

```bash
docker compose up -d postgres
```

Configurer les variables d'environnement :

```bash
export OPENAI_API_KEY=your-openai-api-key
export JWT_SECRET=replace-with-a-secret-at-least-32-characters-long
export POSTGRES_DB_NAME=aiassistant
export POSTGRES_USER_NAME=aiassistant
export POSTGRES_PASSWORD=your-local-password
export APP_CORS_ALLOWED_ORIGINS=http://localhost:4200
```

Démarrer le backend :

```bash
mvn spring-boot:run
```

Un fichier `.env` local peut également être utilisé via `DotEnConfig`.

Le lancement complet de Docker Compose démarre aussi pgAdmin, accessible localement sur `http://localhost:5050`.

## Tests

Exécuter les tests standards :

```bash
mvn test
```

La suite couvre notamment les garde-fous RAG, l'ingestion, l'authentification, les contrôleurs et les migrations Flyway, avec des tests d'intégration utilisant Testcontainers et PostgreSQL/pgVector.

Exécuter séparément l'évaluation RAG avec un véritable modèle OpenAI :

```bash
mvn test -Dgroups=eval -DexcludedGroups=
```

**Attention :** cette commande nécessite une clé OpenAI valide et peut entraîner des coûts d'utilisation de l'API.

## CI/CD et déploiement

### Intégration continue

Le workflow `.github/workflows/ci.yml` s'exécute sur les pushes et pull requests ciblant `main` et `develop`.

Il comprend :

1. Les tests Java et d'intégration.
2. L'analyse SonarQube et son Quality Gate.
3. L'évaluation RAG sur golden dataset.
4. Un GenAI Quality Gate consolidant les vérifications.

### Déploiement manuel

Le workflow `.github/workflows/deploy.yml` permet de déployer volontairement une nouvelle version sur le VPS Contabo.

Le déploiement utilise Docker, GitHub Container Registry (GHCR) et `compose.prod.yaml`.

La procédure détaillée est disponible dans [`docs/DEPLOY.md`](docs/DEPLOY.md).

**Le déploiement n'est pas automatiquement déclenché par la réussite de la CI.**

## Objectif du projet

AI Compliance Copilot est un projet d'apprentissage avancé et de démonstration de compétences en développement Full Stack, intégration GenAI et GenAIOps.

Il illustre un cycle de développement complet :

**Ingest → Retrieve → Generate → Evaluate → Deploy → Observe**

L'objectif n'est pas uniquement de générer des réponses, mais de concevoir une application dans laquelle **les autorisations, les sources, la qualité et les métriques d'exploitation sont des préoccupations de premier ordre**.