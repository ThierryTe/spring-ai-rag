# Déploiement sur VPS Contabo

Pipeline auto-hébergé (plus de dépendance à un repo de workflows partagé) : `deploy.yml`
(`workflow_dispatch`, déclenchement manuel) build l'image Docker, la pousse sur GHCR, puis se
connecte en SSH au VPS pour relancer les conteneurs via `compose.prod.yaml`.

## 1. Mise en place initiale du VPS (une seule fois)

```bash
# Sur le VPS (Ubuntu) :
curl -fsSL https://get.docker.com | sh
sudo apt-get install -y docker-compose-plugin

sudo mkdir -p /opt/spring-ai-rag
cd /opt/spring-ai-rag
```

Créer un fichier `.env` dans ce répertoire (jamais commité, mêmes clés que le `.env` local) :

```
OPENAI_API_KEY=...
JWT_SECRET=...          # au moins 32 caracteres
POSTGRES_DB_NAME=...
POSTGRES_USER_NAME=...
POSTGRES_PASSWORD=...
PGADMIN_DEFAULT_EMAIL=...
PGADMIN_DEFAULT_PASSWORD=...
```

`compose.prod.yaml` est copié automatiquement sur le VPS par le pipeline à chaque déploiement
(pas besoin de le cloner/copier manuellement au préalable) ; `.env` reste géré uniquement à la
main sur le serveur.

## 2. Clé SSH dédiée au déploiement

```bash
# En local :
ssh-keygen -t ed25519 -f deploy_key -C "github-actions-deploy" -N ""
# Copier deploy_key.pub sur le VPS :
ssh-copy-id -i deploy_key.pub <user>@<vps-host>
```

Ne pas réutiliser une clé SSH personnelle : cette clé privée sera stockée dans les secrets GitHub.

## 3. Secrets GitHub à créer

Repo GitHub → Settings → Secrets and variables → Actions (accès admin requis) :

| Secret | Valeur |
|---|---|
| `VPS_HOST` | IP ou nom de domaine du VPS |
| `VPS_USERNAME` | utilisateur SSH sur le VPS |
| `VPS_SSH_KEY` | contenu de `deploy_key` (clé **privée**) |
| `VPS_PORT` | port SSH, optionnel (défaut 22 si absent) |
| `VPS_DEPLOY_PATH` | `/opt/spring-ai-rag` |

Aucun secret supplémentaire n'est nécessaire pour pousser sur GHCR : `deploy.yml` utilise le
`GITHUB_TOKEN` intégré (permission `packages: write` déjà déclarée dans le workflow).

## 4. Rendre l'image GHCR accessible en pull depuis le VPS

Après le premier déclenchement de `deploy.yml`, l'image apparaît dans l'onglet **Packages** du
repo. Par défaut un package est privé : soit le rendre public (Package settings → Change
visibility → Public, le plus simple pour un portfolio, aucune authentification requise côté VPS),
soit garder le package privé et ajouter un `docker login ghcr.io` côté VPS avec un Personal Access
Token `read:packages` (nécessite alors un secret GitHub et une étape supplémentaire dans
`deploy.yml`, non fait par défaut ici).

## 5. Déclencher un déploiement

GitHub → Actions → **Build and Deploy** → Run workflow (branche `master`).

Le job `deploy` échoue explicitement si `/actuator/health` ne répond pas `UP` après redémarrage
(logs du conteneur `app` affichés dans ce cas) plutôt que de rapporter un succès silencieux.

## 6. Accéder à pgAdmin

`compose.prod.yaml` démarre aussi un conteneur `pgadmin` (mêmes identifiants `.env` que le
`postgres` de ce projet), mais son port n'est pas exposé publiquement (bind `127.0.0.1:5050`
uniquement) : le VPS n'a pas de reverse proxy/TLS devant lui, et pgAdmin donne un accès admin
direct à la base — un login exposé nu sur internet est un risque disproportionné par rapport au
port 8080 de l'app. Accès via tunnel SSH :

```bash
ssh -L 5050:localhost:5050 <user>@<vps-host>
# puis, en local :
open http://localhost:5050
```

Ce pgAdmin ne voit que la base de ce projet (réseau Docker isolé à ce `compose.prod.yaml`). Pour
gérer d'autres bases du même VPS depuis la même instance, il faudrait les mettre sur un réseau
Docker partagé ou exposer leurs ports Postgres — pas fait par défaut ici.

## Hors périmètre (volontairement, "pipeline simple")

- Pas de reverse proxy/TLS (Nginx + Let's Encrypt) : l'app est exposée directement sur le port
  8080 du VPS. Prochaine étape naturelle si le projet est exposé publiquement au-delà d'un usage
  portfolio.
- Pas de déploiement continu : le déclenchement reste manuel (`workflow_dispatch`), comme avant
  cette refonte.
