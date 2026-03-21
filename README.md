# Medoq — Plateforme de réservation de médicaments

Medoq est une plateforme B2B2C qui permet aux patients sénégalais de localiser, réserver et payer leurs médicaments dans les pharmacies en temps réel — via application mobile, dashboard web ou USSD.

---

## Table des matières

1. [Architecture](#architecture)
2. [Stack technique](#stack-technique)
3. [Prérequis](#prérequis)
4. [Lancement en local](#lancement-en-local)
5. [Variables d'environnement](#variables-denvironnement)
6. [Déploiement en production (AWS)](#déploiement-en-production-aws)
7. [Référence API](#référence-api)
8. [Structure du projet](#structure-du-projet)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Clients                              │
│  Flutter Mobile App   Angular Dashboard   USSD (*338#)      │
└────────────┬──────────────────┬───────────────┬────────────┘
             │ HTTPS            │ HTTPS / WS     │ AT callback
             ▼                  ▼               ▼
┌─────────────────────────────────────────────────────────────┐
│               Nginx (TLS termination + rate limit)          │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│            Spring Boot 3.2 API  (EC2 / Docker)              │
│  Auth · Search · Reservations · Payments · USSD · Push      │
└──────┬──────────────────┬──────────────────┬────────────────┘
       ▼                  ▼                  ▼
  PostgreSQL 16       Redis 7           AWS S3
  (RDS / local)   (session + cache)  (invoices + assets)
```

**Services externes**
| Service | Usage |
|---|---|
| Wave / Orange Money | Paiement mobile |
| Africa's Talking | SMS + USSD |
| Firebase FCM | Push iOS + Android |
| AWS ECR | Registry Docker |

---

## Stack technique

| Couche | Technologie |
|---|---|
| Backend API | Spring Boot 3.2, Java 21, JPA/Hibernate, Spring Security (JWT) |
| Base de données | PostgreSQL 16 (earthdistance pour la géolocalisation) |
| Cache / sessions | Redis 7 |
| Application mobile | Flutter 3.x, Riverpod, go_router, Dio |
| Dashboard web | Angular 18, Standalone components, Signals |
| CI/CD | GitHub Actions → AWS ECR → EC2 |
| Reverse proxy | Nginx (TLS via Let's Encrypt) |

---

## Prérequis

- **Docker** ≥ 24 + **Docker Compose** v2
- **Java 21** (pour le développement backend sans Docker)
- **Node 20** + **npm** (dashboard Angular)
- **Flutter 3.22** (application mobile)
- Compte Africa's Talking (sandbox gratuit pour les tests)
- Projet Firebase avec FCM activé

---

## Lancement en local

### 1. Cloner le dépôt

```bash
git clone https://github.com/votre-org/medoq.git
cd medoq
```

### 2. Démarrer l'infrastructure (PostgreSQL + Redis)

```bash
docker compose up postgres redis -d
```

Les services sont disponibles sur :
- PostgreSQL : `localhost:5432` — base `medoq_db`, user `medoq_user`, pass `medoq_pass`
- Redis : `localhost:6379`

### 3. Backend Spring Boot

**Option A — avec Docker :**
```bash
docker compose up backend -d --build
# API disponible sur http://localhost:8080/api/v1
```

**Option B — en dehors de Docker (pour le développement) :**
```bash
cd backend
cp ../.env.example ../.env   # éditer les valeurs locales
mvn spring-boot:run
```

Vérifier le démarrage :
```bash
curl http://localhost:8080/api/v1/actuator/health
# {"status":"UP"}
```

### 4. Dashboard Angular

```bash
cd web
npm install
npm start
# http://localhost:4200
```

Le proxy Angular redirige `/api` et `/ws` vers `localhost:8080` (voir `proxy.conf.json`).

### 5. Application Flutter

```bash
cd mobile
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1 \
            --dart-define=MAPS_API_KEY=votre_cle_google_maps
```

> `10.0.2.2` est l'alias Android Emulator vers `localhost` de l'hôte.

### 6. Lancer tous les tests backend

```bash
cd backend
mvn verify
```

---

## Variables d'environnement

Copier `.env.example` → `.env` et remplir chaque variable :

```bash
cp .env.example .env
```

| Variable | Description | Obligatoire |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC PostgreSQL | Oui |
| `SPRING_DATASOURCE_USERNAME` | User DB | Oui |
| `SPRING_DATASOURCE_PASSWORD` | Mot de passe DB | Oui |
| `SPRING_REDIS_HOST` | Host Redis | Oui |
| `SPRING_REDIS_PASSWORD` | Mot de passe Redis | Oui |
| `JWT_SECRET` | Clé secrète JWT (≥64 chars) | Oui |
| `ENCRYPTION_KEY` | Clé AES-256 base64 32 bytes | Oui |
| `WAVE_API_KEY` | Clé API Wave | Paiements Wave |
| `WAVE_WEBHOOK_SECRET` | Secret webhook Wave | Paiements Wave |
| `ORANGE_MERCHANT_KEY` | Clé marchand Orange Money | Paiements OM |
| `AT_API_KEY` | Clé Africa's Talking | SMS + USSD |
| `AT_USERNAME` | Username AT | SMS + USSD |
| `FCM_SERVICE_ACCOUNT_PATH` | Chemin JSON compte de service | Push notifications |
| `AWS_ACCESS_KEY_ID` | Clé AWS (dev/CI seulement) | S3 |
| `AWS_SECRET_ACCESS_KEY` | Secret AWS (dev/CI seulement) | S3 |
| `AWS_REGION` | Région AWS (ex: `eu-west-1`) | S3 + ECR |

Générer des secrets sécurisés :
```bash
# JWT secret
openssl rand -base64 64

# Clé AES-256
openssl rand -base64 32
```

---

## Déploiement en production (AWS)

### Infrastructure recommandée

| Composant | Service AWS | Spécification minimale |
|---|---|---|
| API Backend | EC2 | `t3.small` (2 vCPU, 2 GB RAM) |
| Base de données | RDS PostgreSQL 16 | `db.t3.micro` — Multi-AZ en prod |
| Cache / USSD sessions | ElastiCache Redis 7 | `cache.t3.micro` |
| Stockage factures | S3 | Bucket `medoq-invoices-prod` |
| Stockage assets | S3 | Bucket `medoq-assets-prod` |
| Registry Docker | ECR | Repo `medoq-backend` |
| DNS + TLS | Route 53 + Let's Encrypt | `api.medoq.sn` |

### Étape 1 — Créer les ressources AWS

**ECR — Registry Docker :**
```bash
aws ecr create-repository --repository-name medoq-backend --region eu-west-1
```

**S3 — Buckets :**
```bash
aws s3 mb s3://medoq-invoices-prod --region eu-west-1
aws s3 mb s3://medoq-assets-prod   --region eu-west-1

# Bloquer l'accès public
aws s3api put-public-access-block \
  --bucket medoq-invoices-prod \
  --public-access-block-configuration "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
```

**IAM — Rôle EC2 :**
Créer un rôle EC2 et y attacher la policy `infra/aws/iam-policy.json`.
Le rôle permet à l'instance de puller depuis ECR et d'écrire dans S3 sans clés hardcodées.

### Étape 2 — Préparer l'instance EC2

```bash
# Lancer l'instance avec le user-data
aws ec2 run-instances \
  --image-id ami-0c55b159cbfafe1f0 \    # Amazon Linux 2023
  --instance-type t3.small \
  --key-name medoq-key \
  --security-group-ids sg-xxxxxxxx \
  --iam-instance-profile Name=medoq-ec2-role \
  --user-data file://infra/aws/ec2-userdata.sh \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=medoq-backend}]'
```

**Groupes de sécurité (Security Groups) :**
```
Inbound:
  - 22   (SSH)    — votre IP seulement
  - 80   (HTTP)   — 0.0.0.0/0  (Nginx redirige vers HTTPS)
  - 443  (HTTPS)  — 0.0.0.0/0
  - 8080 (API)    — sg EC2 seulement (accès interne Nginx)

Outbound:
  - Tout (pour ECR pull, AT SMS, FCM, Wave, Orange API)
```

### Étape 3 — Déployer l'application

```bash
# Copier le fichier .env sur le serveur
scp .env ec2-user@<EC2_IP>:/opt/medoq/.env

# Copier la clé de service Firebase
scp firebase-service-account.json ec2-user@<EC2_IP>:/opt/medoq/secrets/

# Premier déploiement manuel
ssh ec2-user@<EC2_IP>
cd /opt/medoq
aws ecr get-login-password --region eu-west-1 | \
  docker login --username AWS --password-stdin <ECR_REGISTRY>
docker pull <ECR_REGISTRY>/medoq-backend:latest
docker tag  <ECR_REGISTRY>/medoq-backend:latest medoq-backend:latest
docker compose up -d
```

### Étape 4 — TLS avec Let's Encrypt

```bash
ssh ec2-user@<EC2_IP>
sudo dnf install -y nginx certbot python3-certbot-nginx

# Copier la config Nginx
sudo cp /chemin/vers/infra/nginx/medoq.conf /etc/nginx/sites-available/medoq
sudo ln -s /etc/nginx/sites-available/medoq /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# Obtenir le certificat
sudo certbot --nginx -d api.medoq.sn
```

### Étape 5 — CI/CD automatique (GitHub Actions)

Ajouter ces secrets dans **Settings → Secrets → Actions** :

| Secret | Valeur |
|---|---|
| `AWS_ACCESS_KEY_ID` | Clé IAM du compte de déploiement |
| `AWS_SECRET_ACCESS_KEY` | Secret IAM |
| `EC2_HOST` | IP publique ou DNS de l'instance |
| `EC2_USER` | `ec2-user` (Amazon Linux) ou `ubuntu` |
| `EC2_SSH_KEY` | Contenu de la clé privée SSH |

Ajouter ces variables dans **Settings → Variables → Actions** :

| Variable | Valeur |
|---|---|
| `AWS_REGION` | `eu-west-1` |
| `ECR_REGISTRY` | `123456789012.dkr.ecr.eu-west-1.amazonaws.com` |

Après configuration, chaque push sur `main` déclenche automatiquement :
1. Tests Java + Angular build + Flutter analyze
2. Build image Docker + push vers ECR
3. `docker compose up --force-recreate` sur EC2
4. Vérification du health check

---

## Référence API

**Base URL** : `https://api.medoq.sn/api/v1`

Toutes les réponses sont en JSON sauf `/ussd` (texte brut).
Les endpoints protégés nécessitent l'en-tête : `Authorization: Bearer <access_token>`

### Authentification

| Méthode | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/auth/register` | — | Créer un compte patient |
| `POST` | `/auth/register-pharmacy` | — | Créer un compte pharmacie |
| `POST` | `/auth/login` | — | Connexion (retourne access + refresh tokens) |
| `POST` | `/auth/refresh` | — | Rafraîchir l'access token |
| `POST` | `/auth/logout` | Oui | Invalider le token |
| `POST` | `/auth/forgot-password` | — | Demander un OTP de réinitialisation |
| `POST` | `/auth/reset-password` | — | Réinitialiser le mot de passe avec OTP |

**Exemple — Login :**
```json
POST /auth/login
{
  "phone": "+221771234567",
  "password": "MonMotDePasse1"
}

// Réponse 200
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "user": {
    "id": "uuid",
    "phone": "+221771234567",
    "firstName": "Amadou",
    "role": "CUSTOMER"
  }
}
```

---

### Médicaments

| Méthode | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/medications/popular` | — | Top médicaments recherchés |
| `GET` | `/medications/search?q=...&lat=...&lng=...&radius=...` | — | Recherche avec stock par pharmacie |
| `GET` | `/medications/{id}` | — | Détail + stock par pharmacie |

**Paramètres `/medications/search` :**

| Paramètre | Type | Requis | Description |
|---|---|---|---|
| `q` | string | Oui | Nom (min 2 chars) |
| `lat` | double | Non | Latitude (avec `lng`) |
| `lng` | double | Non | Longitude (avec `lat`) |
| `radius` | double | Non | Rayon km (défaut: 5.0, max: 50) |

---

### Pharmacies

| Méthode | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/pharmacies/nearby?lat=...&lng=...&radius=...` | — | Pharmacies proches (géolocalisation) |

---

### Réservations

| Méthode | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/reservations` | Oui | Créer une réservation |
| `GET` | `/reservations/{id}` | Oui | Détail d'une réservation |
| `GET` | `/reservations/patient/{patientId}` | Oui | Réservations d'un patient |
| `GET` | `/reservations/pharmacy/{pharmacyId}` | Oui | Réservations d'une pharmacie |
| `PATCH` | `/reservations/{id}/confirm` | Oui (pharmacie) | Confirmer une réservation |
| `PATCH` | `/reservations/{id}/cancel` | Oui | Annuler une réservation |
| `PATCH` | `/reservations/{id}/complete` | Oui (pharmacie) | Marquer comme complétée |

**Statuts de réservation :**
`PENDING` → `CONFIRMED` → `PAID` → `READY` → `COMPLETED`
`PENDING/CONFIRMED/PAID` → `CANCELLED`
`PENDING/CONFIRMED` → `EXPIRED`

**Exemple — Créer une réservation :**
```json
POST /reservations
Authorization: Bearer <token>
{
  "pharmacyId": "uuid-pharmacie",
  "customerId": "uuid-patient",
  "items": [
    { "medicationId": "uuid-med", "quantity": 2 }
  ]
}

// Réponse 201
{
  "id": "uuid",
  "reference": "MQ-260321-01000",
  "status": "PENDING",
  "expiresAt": "2026-03-21T14:00:00Z",
  "total": 1000.00
}
```

---

### Paiements

| Méthode | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/payments/wave/initiate` | Oui | Initier un paiement Wave |
| `POST` | `/payments/orange/initiate` | Oui | Initier un paiement Orange Money |
| `GET` | `/payments/{id}` | Oui | Statut d'un paiement |
| `GET` | `/payments/reservation/{reservationId}` | Oui | Paiement d'une réservation |

**Exemple — Initier Wave :**
```json
POST /payments/wave/initiate
Authorization: Bearer <token>
{
  "reservationId": "uuid-reservation"
}

// Réponse 200
{
  "checkoutUrl": "https://pay.wave.com/c/xxxxxxxx",
  "paymentId": "uuid-payment"
}
```

---

### Webhooks (publics — signés)

| Méthode | Endpoint | Description |
|---|---|---|
| `POST` | `/webhooks/wave` | Callback Wave (HMAC-SHA256 vérifié) |
| `POST` | `/webhooks/orange-money` | Callback Orange Money (notif_token vérifié) |

---

### USSD

| Méthode | Endpoint | Description |
|---|---|---|
| `POST` | `/ussd` | Callback Africa's Talking (form-urlencoded, texte brut) |

**Menu USSD `*338#` :**
```
1. Rechercher un médicament
   └─ Saisir nom → liste pharmacies → confirmer → payer Orange Money
2. Mes réservations
   └─ Liste → détail
3. Annuler une réservation
   └─ Numéro de référence → confirmation
```

---

### Notifications (tokens FCM)

| Méthode | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/users/{userId}/device-tokens` | Oui (propriétaire) | Enregistrer un token FCM |
| `DELETE` | `/users/{userId}/device-tokens/{token}` | Oui (propriétaire) | Supprimer un token FCM |

---

### Santé

| Méthode | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/actuator/health` | — | État de l'application |

---

## Structure du projet

```
medoq/
├── backend/                    # Spring Boot API
│   ├── src/main/java/com/medoq/backend/
│   │   ├── config/             # Security, Firebase, CORS, Redis
│   │   ├── controller/         # REST controllers
│   │   ├── dto/                # Request / Response DTOs
│   │   ├── entity/             # JPA entities
│   │   ├── repository/         # Spring Data repositories
│   │   ├── security/           # JWT filter, service
│   │   └── service/            # Business logic
│   │       ├── ussd/           # USSD flow + session
│   │       ├── wave/           # Wave API client
│   │       └── orange/         # Orange Money API client
│   └── Dockerfile
│
├── web/                        # Angular 18 dashboard pharmacie
│   └── src/app/
│       ├── core/               # Services, interceptors, guards, models
│       ├── features/           # dashboard, stock, reservations, alerts, analytics, profile
│       └── shell/              # Layout avec sidebar
│
├── mobile/                     # Flutter application patient
│   └── lib/
│       ├── core/               # Theme, router, network, storage, widgets
│       └── features/           # auth, search, reservation, payment
│
├── infra/
│   ├── postgres/
│   │   └── init.sql            # Schéma initial PostgreSQL
│   ├── nginx/
│   │   └── medoq.conf          # Config Nginx (reverse proxy + TLS)
│   └── aws/
│       ├── ec2-userdata.sh     # Bootstrap EC2 (Docker + AWS CLI)
│       └── iam-policy.json     # Politique IAM minimale
│
├── .github/
│   └── workflows/
│       ├── ci.yml              # Tests à chaque push (Java + Angular + Flutter)
│       └── deploy.yml          # Build ECR + déploiement EC2 sur push main
│
├── docker-compose.yml          # Dev local (postgres + redis + backend)
├── .env.example                # Template des variables d'environnement
└── README.md
```

---

## Coûts AWS estimés (production minimale)

| Service | Type | Coût/mois (USD) |
|---|---|---|
| EC2 `t3.small` | Backend API | ~15 |
| RDS `db.t3.micro` PostgreSQL | Base de données | ~15 |
| ElastiCache `cache.t3.micro` | Redis | ~12 |
| S3 (10 GB) | Factures + assets | ~0.25 |
| ECR (5 GB) | Images Docker | ~0.50 |
| Route 53 | DNS | ~0.50 |
| **Total estimé** | | **~43 USD/mois** |

> Pour réduire les coûts en phase MVP : utiliser une seule instance EC2 avec Docker Compose (PostgreSQL + Redis + API sur la même machine) et supprimer RDS/ElastiCache. Coût estimé : ~15 USD/mois.
