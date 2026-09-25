# one-piece-user-service

Backend applicativo (Spring Boot) per l'identità e la gestione utenti di One Piece API:
risolve l'identità del chiamante dal JWT (`/api/me`), gestisce l'invito e il ciclo di vita
degli account via Keycloak Admin API, e il catalogo ruoli/permessi.

- **Flussi/regole di prodotto:** `docs/user-flows/authentication-and-user-management.md` (repo `one-piece-api`).
- **Stack:** vedi `docs/technology-stack.md` (repo `one-piece-api`).
- **Decisioni architetturali di questo servizio:** `docs/adr/`.

## Sviluppo locale

Prerequisito: credenziali per GitHub Packages (da cui arriva `one-piece-exception`), cioè un
[personal access token classic](https://github.com/settings/tokens) con il solo scope
`read:packages`, in `~/.gradle/gradle.properties`:

```properties
gpr.user=<utente GitHub>
gpr.token=<token>
```

Poi il cluster `kind` locale attivo (`./scripts/setup.sh` nel repo
`onepiece-infrastructure`) e questi due port-forward, in due terminali separati:

```bash
kubectl port-forward svc/keycloak-http -n auth 8080:8080
kubectl port-forward svc/one-piece-postgresql -n data 5433:5432
```

**Da IntelliJ IDEA:** apri il progetto (Gradle lo importa automaticamente),
poi esegui la run configuration generata per `UserServiceApplication`
(`src/main/java/dev/onepieceapi/userservice/UserServiceApplication.java`).
Il profilo Spring `local` è già quello attivo di default
(`application.properties`), quindi non serve impostare nessuna variabile
d'ambiente o VM option: parte già puntando ai port-forward sopra. Il servizio
risponde su `http://localhost:8081/api/...`
(`http://localhost:8081/api/actuator/health` per verificare che sia su).

**Da riga di comando**, equivalente:

```bash
./gradlew bootRun
```

**Test e formattazione:**

```bash
./gradlew check            # test + verifica formattazione (spring-javaformat)
./gradlew format           # applica la formattazione automaticamente
```

**API: spec OpenAPI, Swagger UI, Bruno** (vedi `docs/adr/0014-openapi-contract-and-bruno-collection.md`):

- Swagger UI: `http://localhost:4180/api/swagger-ui.html` (dopo il login; in remoto stesso path sull'IP pubblico).
- Spec committata: `openapi/openapi.yaml`. Dopo una modifica alle API:

  ```bash
  ./gradlew updateOpenApiSpec              # rigenera openapi/openapi.yaml (il test fallisce se è disallineata)
  ./scripts/generate-bruno-collection.sh   # rigenera bruno/ dalla spec (richiede Node.js)
  ```

- Bruno: "Open Collection" su `bruno/`, environment `dev` (servizio avviato da IntelliJ,
  `localhost:8081`, senza proxy), `local` (cluster `kind` via oauth2-proxy) o `remote` (per `remote` copia
  `bruno/.env.example` in `bruno/.env` e imposta `REMOTE_HOST`). Il primo invio apre il
  login Keycloak (client `bruno`, PKCE).

**Verifica nel cluster `kind`** (build immagine reale, non solo il processo
locale): `scripts/deploy-local.sh` (build immagine + `kind load` + rollout
restart del Deployment).
