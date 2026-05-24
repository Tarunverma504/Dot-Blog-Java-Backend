# Dot-Blog Java Microservices Backend

A Java 21 + Spring Boot 3 re-implementation of the original Node.js Dot-Blog backend (`../server`). The Node service is left untouched; this is a parallel revamp that drops in behind the existing React client without any frontend changes — same `/api/v2/*` routes, same response shapes.

## What's inside

| Service | Port (default) | Purpose |
|---------|---------------:|---------|
| `gateway` | 8080 | Public entry point. Routes `/api/v2/*` to the right downstream service, terminates CORS. |
| `auth-service` | 8081 | Register / login / OTP verify / forgot+reset password / `isAuthenticated`. Signs JWTs. Sends email via Resend. |
| `user-service` | 8082 | Profile read/update, author lookup, profile + cover photos. |
| `blog-service` | 8083 | Blog CRUD, publish, soft-delete, list endpoints, thumbnail upload. |
| `engagement-service` | 8084 | Like / dislike / add / delete comment, writes directly to the shared `blogs` collection. |
| `media-service` | 8085 | The only service that holds Cloudinary credentials. Exposes `/internal/upload` + `/internal/delete` to siblings; not reachable from outside the docker network. |
| `shared/api-contract` | – | Common DTOs reused across services. |
| `shared/events` | – | Future event DTOs for async messaging. |

All services share one MongoDB database (`dot-blog` on Atlas in the default configuration). Inter-service calls go over the docker bridge network via `RestClient`. The full topology and request flow are in [docs/DOCKER_ARCHITECTURE.md](docs/DOCKER_ARCHITECTURE.md).

## Prerequisites

- **Java 21** (Temurin / Adoptium recommended)
- **Maven 3.8+** (the `./mvnw` wrapper in this directory handles it if you don't have one installed)
- **Docker + Docker Compose** (the recommended way to run everything)
- A **MongoDB connection string** (free Atlas cluster works perfectly; local mongod also fine)
- API keys for **Resend** (email) and **Cloudinary** (image storage) — both have generous free tiers

## Quick start (Docker, recommended)

This brings up the whole stack — 6 Java services + a local MongoDB container if you don't override `DATABASE_URI`.

```bash
cd dot-blog-backend
cp .env.example .env
# Edit .env: paste your Atlas URI, generate a JWT_SECRET (openssl rand -base64 48),
# add your Resend + Cloudinary credentials. See "Environment variables" below.

docker compose up -d
docker compose ps               # wait for everything to be "healthy"
curl http://localhost:8080/actuator/health  # -> {"status":"UP"}
```

The first build pulls the Maven base image and compiles all 6 modules; budget ~5 minutes the first time, ~30 seconds on subsequent rebuilds. Once healthy, point the React app's `REACT_APP_PORT` at `http://localhost:8080` and you're live.

To tear down:

```bash
docker compose down            # stop, keep volumes
docker compose down -v         # stop and wipe the local MongoDB volume
```

## Quick start (Maven, no Docker)

Useful for IDE debugging when you want to step through one service at a time. Each service expects MongoDB to already be reachable via `DATABASE_URI`, and downstream services to be on their default ports.

```bash
cd dot-blog-backend
./mvnw clean install -DskipTests        # one-time, builds all modules

# Then in 6 terminals (or use scripts/start-all.sh):
( cd gateway            && ../mvnw spring-boot:run )
( cd auth-service       && ../mvnw spring-boot:run )
( cd user-service       && ../mvnw spring-boot:run )
( cd blog-service       && ../mvnw spring-boot:run )
( cd engagement-service && ../mvnw spring-boot:run )
( cd media-service      && ../mvnw spring-boot:run )
```

Each service auto-loads `dot-blog-backend/.env` on startup via [spring-dotenv](https://github.com/paulschwarz/spring-dotenv) — you don't need to `export` anything manually. Real OS env vars always win over `.env`, which is what you want in production.

## Environment variables

All keys live in a single `dot-blog-backend/.env` file (gitignored). See [.env.example](.env.example) for the full template with comments.

| Variable | Required by | Purpose |
|----------|-------------|---------|
| `DATABASE_URI` | auth, user, blog, engagement | Mongo connection string. Atlas SRV format or `mongodb://…` for local. |
| `JWT_SECRET` | auth, user, blog, engagement | Shared HMAC secret. Must be identical across services. Use `openssl rand -base64 48` for production. |
| `TWO_WAY_SECRET` | auth | Symmetric key for OTP-payload encryption (Node parity). |
| `RESEND_API_KEY` | auth | API key from https://resend.dev. |
| `SENDER_MAIL_ID` | auth | `From:` address. Must be on a Resend-verified domain for production. |
| `FRONTEND_URL` | auth | Used to build password-reset links emailed to users. |
| `CLOUD_NAME` | media | Cloudinary cloud name. Only media-service ever reads this. |
| `CLOUDINARY_API_KEY` | media | Cloudinary API key. |
| `CLOUDINARY_API_SECRET` | media | Cloudinary API secret. |

The React client has its own separate template at [`../client/.env.example`](../client/.env.example) covering `REACT_APP_PORT` and the two Cloudinary keys it needs for direct-from-browser uploads.

## Verifying the stack

After `docker compose up -d` (or the Maven equivalent):

```bash
# 1. Gateway is reachable
curl http://localhost:8080/actuator/health

# 2. Each downstream service responds (when running locally)
for p in 8081 8082 8083 8084 8085; do
  echo "$p:"; curl -s http://localhost:$p/actuator/health
done

# 3. End-to-end smoke test
./scripts/smoke.sh
```

[`scripts/smoke.sh`](scripts/smoke.sh) walks the full happy path (register → login → isAuthenticated → create blog → publish → list → like → comment → owner-delete → confirm gone from every read endpoint → non-owner gets 403). Last verified green at 17/17. Run it against any deploy with `BASE_URL=https://your-host ./scripts/smoke.sh`.

## API endpoints

All endpoints are documented with their current implementation status in [../docs/IMPLEMENTATION_STATUS.md](../docs/IMPLEMENTATION_STATUS.md). The base path is `/api/v2/*` through the gateway on port 8080; the React client uses these unchanged from the original Node implementation.

Notable additions in the Java rewrite that aren't in `../server`:

- `POST /api/v2/delete-blog/{id}` — owner-only soft delete. Sets `hidden:true` on the blog. All read endpoints (`get-all-blogs`, `get-blog/:id`, `get-blogs`, `get-user-blogs`, `get-categories-blogs`, `Author/:id`) filter `hidden:true` out, so the blog is gone for everyone — owner included — while the document is preserved in Mongo for safety.

## Architecture notes

- **Single shared database.** Auth-service writes to `users`, blog-service to `blogs`, user-service touches profile fields on `users` via `$set`/`$push` (never blowing away auth-owned fields), engagement-service writes directly to the shared `blogs` collection using raw `Document` operations so it doesn't import blog-service's domain class.
- **Shared JWT.** Auth-service signs; user-service, blog-service, and engagement-service verify. The JWT contains the userId in the `data` claim — Node compatibility.
- **Cross-service author lookups.** Blog-service and engagement-service batch-fetch author summaries from user-service via `POST /internal/users/summary` instead of doing a Mongo `populate` (which would require importing the user model).
- **Centralized Cloudinary.** Only media-service holds the credentials and the SDK. Blog-service and user-service call it via `MediaClient` (`RestClient`) and the `/api/v2/upload-thumbnail` route at the gateway still terminates at blog-service so the JWT is validated before proxying the multipart through.
- **Mixed-type `userId` in legacy data.** Atlas docs created by the old Node app store `userId` as a BSON `ObjectId`; new docs created by Java store it as a `String`. A `userIdMatches(...)` helper in `BlogService` builds an `$or` criterion that matches both, so queries work uniformly across both eras. A one-time migration script can be added later as a clean-up; not required.

Full container topology and request flow examples (with diagrams) are in [docs/DOCKER_ARCHITECTURE.md](docs/DOCKER_ARCHITECTURE.md).

## Testing

- **Unit / integration tests** (Testcontainers spins up a throwaway Mongo):
  ```bash
  ./mvnw test
  ./mvnw test -pl auth-service                 # just auth
  ```
- **Auth-only curl walk** (legacy script, kept for quick debugging):
  ```bash
  BASE_URL=http://localhost:8080 ./scripts/test-auth-curl.sh
  ```
- **Full E2E smoke** (recommended after any change):
  ```bash
  ./scripts/smoke.sh
  BASE_URL=https://your-deploy.example.com ./scripts/smoke.sh
  ```

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `docker compose up` exits with "no such file or directory" on `media-service` | You're on an older `pom.xml` parent that doesn't list `media-service`. | `git pull`; the parent pom should include the `media-service` module. |
| Gateway returns 401 on every authenticated request | `JWT_SECRET` in `.env` doesn't match what auth-service used to sign tokens — possibly two different values across services. | Make sure `JWT_SECRET` is identical for all services. After changing it, every user must log in again because old tokens won't verify. |
| Auth-service can't reach MongoDB Atlas (`MongoSocketException`) | Atlas Network Access list doesn't include your egress IP. | Add your current IP (or `0.0.0.0/0` for testing) in Atlas → Network Access. |
| Other users' profiles show `0 published blogs` | Legacy data has `userId` as `ObjectId` in some blogs and `String` in others. | The `userIdMatches` helper in [blog-service/.../BlogService.java](blog-service/src/main/java/com/dotblog/blog/service/BlogService.java) already handles this — make sure blog-service was rebuilt and bounced after a code update. |
| React client uploads fail with `cloud_name is disabled` or `https://api.cloudinary.com/v1_1/undefined/...` | `client/.env` is missing `REACT_APP_CLOUD_NAME` and/or `REACT_APP_UPLOAD_PRESET`, or the preset isn't set to "Unsigned" in Cloudinary. | Fill in `client/.env`, restart `npm start`. Make sure the preset's Signing Mode = Unsigned. |
| Smoke test fails on step 6 with `{"message":"Blogid required"}` | The publish-blog body field is `Blogid` (Node casing quirk), not `blogId`. The shipped `smoke.sh` already uses the right key. | Update any custom callers to send `{"Blogid": "..."}`. |

## Migration plan

The day-by-day migration plan and overall strategy live in:

- [../docs/MIGRATION_DAY_WISE_PLAN.md](../docs/MIGRATION_DAY_WISE_PLAN.md)
- [../docs/BACKEND_MIGRATION_PLAN_NODE_TO_JAVA_MICROSERVICES.md](../docs/BACKEND_MIGRATION_PLAN_NODE_TO_JAVA_MICROSERVICES.md)
- [../docs/IMPLEMENTATION_STATUS.md](../docs/IMPLEMENTATION_STATUS.md) — current progress, endpoint-by-endpoint status, and architecture decisions.
