# HaaS Demo: Hardware-as-a-Service

**React + TypeScript** frontend · **Java + Spring Boot** backend · **MongoDB** (two databases)

A small reference app for sharing lab equipment across projects. It has three windows
(Login → Project Management → Resource Page), uses simulated users and hardware, and keeps user
data and hardware data in **two separate MongoDB databases**.

## Run it

Needs Java 21+ and Maven. Node is optional because the build downloads its own.

```bash
mvn spring-boot:run
```

Then open http://localhost:8080.

- The first build downloads Node into `frontend/node/`, then type-checks and builds the React app. A TypeScript error fails the Maven build.
- The first run downloads a MongoDB server binary from mongodb.org (about 70 MB) and caches it in `~/.embedmongo`.
- MongoDB's data files go in `./data/mongo`, so data survives restarts. To reset the demo, stop the app and delete `./data/`.

**Demo accounts:** `ada`, `grace`, `linus`, all with the password `Demo#2026!`. Pick one from the
"User choose" dropdown. `ada` and `grace` share project `AMPLAB1`, and
`linus` owns `SENSOR7`. The seed inventory is three sets of lab equipment: `Oscilloscopes`, `Function-Generators` and `Power-Supplies`.

### Frontend development (hot reload)

```bash
mvn spring-boot:run -Dskip.npm -Dskip.installnodenpm
```

```bash
cd frontend && npm install && npm run dev
```

Open http://localhost:5173 (`npm run typecheck` runs the TypeScript compiler without building). Vite forwards `/api/*` to Spring Boot on port 8080, so the browser sees a
single origin. The session cookie works and no CORS setup is needed.

### Tests

```bash
mvn test -Dskip.npm -Dskip.installnodenpm
```

Each test run gets its own throwaway embedded MongoDB. The tests cover:
- encryption at rest
- that the two databases are separate
- checkout and check-in arithmetic
- overdraw protection
- that 40 simultaneous checkouts never oversell
- field-path injection
- project membership

### Deploying against a real MongoDB (e.g. Atlas)

```bash
mvn package
```

```bash
SPRING_PROFILES_ACTIVE=prod MONGODB_URI='mongodb+srv://…' HAAS_CRYPTO_KEY='<base64 32 bytes>' java -jar target/haas-demo-0.1.0.jar
```

The `prod` profile turns off embedded MongoDB and connects to `MONGODB_URI`. The React app is bundled
inside the jar, so the whole app is one deployable at one URL. Keep the URI and
the key in the host's environment settings, never in the repo.

### Static demo on GitHub Pages

`.github/workflows/pages.yml` runs the backend tests, then publishes the React app to GitHub Pages
on every push to `master` (or on demand from the Actions tab). Pages can't run Spring Boot or
MongoDB, so this build (`npm run build:pages`, i.e. `vite build --mode pages`) swaps the HTTP calls
in `api.ts` for `frontend/src/mockBackend.ts`. That's an in-browser copy of the API with the same routes,
rules, error messages and seed data, storing everything in the visitor's `localStorage`. Each visitor
gets their own copy of the data; clear site data to reset it. The normal `npm run build` / jar
build doesn't include the mock.

One-time setup: **Settings → Pages → Source: GitHub Actions**. The site is public at
`https://<user>.github.io/<repo>/`, even if the repo is private.

When you change the seed data in `application.yml`, update the matching block in `mockBackend.ts`.

## Architecture

```
React+TS (frontend)                      Spring Boot                                          MongoDB
┌──────────────────────┐   /api/auth     ┌─────────────┐   ┌──────────────────┐   ┌───────────────────────────┐
│ LoginPage            │ ─────────────▶ │ AuthCtrl    │──▶│ UserService      │──▶│ haas_users                │
│ ProjectsPage         │   /api/projects │ ProjectCtrl │──▶│ ProjectService   │   │   users, projects         │
│ ResourcePage         │ ─────────────▶ │             │   │ CredentialCipher │   └───────────────────────────┘
│ FormDialog (popups)  │   /api/hardware │ HardwareCtrl│─┬▶│ (membership chk) │   ┌───────────────────────────┐
└──────────────────────┘ ─────────────▶ └─────────────┘ └▶ HardwareService ───▶│ haas_hardware             │
                                                                                 │   hardware_sets           │
                                                                                 └───────────────────────────┘
```

| Where | Responsibility |
|---|---|
| `frontend/src/types.ts` | TypeScript interfaces for every API response. Each mirrors a Java record (`ProjectView`, `HardwareView`); change both together. |
| `frontend/src/api.ts` | Every HTTP call the UI makes, typed end to end, with `ApiError` carrying the HTTP status. The only file that knows the URLs. |
| `frontend/src/pages/*.tsx` | The three windows |
| `frontend/src/components/FormDialog.tsx` | One generic popup component, reused for create-account, change-password and return-hardware. Its `fields` prop types the values `onSubmit` receives. |
| `config/UsersMongoConfig`, `HardwareMongoConfig` | One `MongoTemplate` per database. The package a repository is in decides which database it uses. Also creates the unique indexes. |
| `user/` | Accounts and projects (`haas_users`) |
| `hardware/` | Hardware sets and per-project checkouts (`haas_hardware`) |
| `security/` | AES-GCM user-ID encryption, HMAC lookup key, BCrypt |
| `web/` | REST controllers, session handling, JSON error mapping |
| `seed/` | Loads simulated data from `haas.seed` in `application.yml` when a database is empty |

### Documents

```jsonc
// haas_users.users
{ "_id": ObjectId, "userIdLookup": "<hmac hex, unique>", "userIdCipher": "<AES-GCM base64>",
  "passwordHash": "<bcrypt>", "demo": true, "createdAt": ISODate }

// haas_users.projects
{ "_id": ObjectId, "projectId": "AMPLAB1", "projectKey": "amplab1" /* unique */, "name": "…",
  "description": "…", "ownerId": "<user _id>", "memberIds": ["<user _id>", …], "createdAt": ISODate }

// haas_hardware.hardware_sets
{ "_id": "Oscilloscopes", "description": "…", "capacity": 60, "available": 48,
  "allocations": { "AMPLAB1": 12 } }      // invariant: available + sum(allocations) == capacity
```

## REST API

| Method | Path | Body | Notes |
|---|---|---|---|
| GET | `/api/auth/demo-users` | — | Fills the login dropdown (`haas.demo.user-chooser`) |
| POST | `/api/auth/register` | `userId, password, confirmPassword` | "Create account" popup |
| POST | `/api/auth/login` | `userId, password` | Starts a session (HTTP-only, SameSite=Strict cookie) |
| POST | `/api/auth/change-password` | `userId, oldPassword, newPassword, confirmPassword` | "Forgot password" popup |
| POST | `/api/auth/logout` · GET `/api/auth/me` | — | |
| GET | `/api/projects` | — | Projects the user belongs to |
| POST | `/api/projects` | `projectId, name, description` | Create a new project |
| POST | `/api/projects/{id}/access` | — | Use an existing project (joins it) |
| GET | `/api/hardware?projectId=` | — | Capacity, available, and this project's holdings |
| POST | `/api/hardware/{set}/checkout` | `projectId, quantity` | |
| POST | `/api/hardware/{set}/checkin` | `projectId, quantity` | |

Every error comes back as `{"error": "..."}` with a 400, 401, 403, 404 or 409 status.

## Design decisions

- **Atomic checkout without transactions.**
  - Each hardware set's per-project counts (`allocations`) live in the same document as `available`.
  - A checkout is then one `findAndModify` whose filter requires `available >= qty` and which applies two `$inc`s.
  - MongoDB applies a single-document update atomically, so the stock can never go below zero.
  - This needs no multi-document transactions, which would require a replica set. The concurrency test proves it: exactly 10 of 40 racing requests succeed against 50 units.
- **Uniqueness comes from database indexes, not from check-then-insert.**
  - `userIdLookup` and `projectKey` have unique indexes.
  - The service just inserts and turns a `DuplicateKeyException` into a 409.
  - Joining a project uses `$addToSet`, so two people joining at the same moment can't overwrite each other.
- **Why store the user ID two ways?**
  - AES-GCM encryption is reversible, so the app can show your user ID back to you. But the same ID encrypts differently every time, so you can't search by it.
  - The HMAC is deterministic, so it works as a unique index for looking up users at login.
- **The cross-database boundary.**
  - The hardware database refers to projects by their code (`AMPLAB1`), not by an internal ID.
  - `HardwareController` is the only class that uses both services: it checks membership first, then updates the hardware.
  - Project IDs become MongoDB field names (`allocations.AMPLAB1`), so `HardwareService` rejects any ID containing `.` or `$`, whoever the caller is.
- **TypeScript on the frontend.**
  - `tsconfig.json` turns on `strict` and `noUncheckedIndexedAccess`.
  - The build runs `tsc` before `vite build`, so a type error fails `npm run build` and `mvn package`.
  - Every API response has an interface in `types.ts`, so a component that reads a field the server doesn't send won't compile.
- **Anticipated changes, and where they're encapsulated.**
  - Moving from embedded MongoDB to Atlas only changes a profile, with no code changes.
  - The encryption scheme lives in `CredentialCipher` and `UserService`.
  - The seed data lives in `application.yml`.
  - The API URLs live in `frontend/src/api.ts`, and the response shapes in `types.ts`.
  - The popup behavior lives in one component, `FormDialog`.
- **Known tradeoffs.**
  - One hardware document holds every project's count. That's fine for a handful of sets, but thousands of projects per set would make the documents large.
  - There is no transaction spanning both databases. That's acceptable here because the membership check only reads.
- **Possible improvements.**
  - The validation rules are written twice, in `validation.ts` and in the Java services. The server could publish them instead.
  - `types.ts` is maintained by hand. It could be generated from an OpenAPI spec (e.g. springdoc + openapi-typescript) so the TypeScript and Java types can't drift apart.
  - `ApiException` could be replaced with Spring's `ProblemDetail`.
  - The hand-rolled session auth could be replaced with Spring Security.

## UI notes

- The check-in popup's text says "Enter compute amount to **return**". In this demo:
  - **Check out** takes its amount from the Request field on the row.
  - **Check in** opens the return popup.
- "Forgot password" asks for the old password, then the new password twice, so it's a change-password form. True password recovery would need email, which is outside this demo's scope.
