# Dot-Blog Docker Architecture

Local-dev stack for the Java microservices migration. One `docker compose up -d` brings up 7 containers on a single bridge network, with only `:8080` published to the host.

| Containers | Java services | Public port | Bridge network |
|---|---|---|---|
| **7** | **5** | `:8080` (gateway only) | `dotblog` (single) |

---

## Topology at a glance

```
                ┌──────────────┐      ┌──────────────────┐
                │  React app   │      │  curl / Postman  │
                │ localhost:3000│      │       host       │
                └──────┬───────┘      └────────┬─────────┘
                       │                       │
                       └───────────┬───────────┘
                                   │  HTTP /api/v2/*
                                   ▼
                       ┌───────────────────────┐
                       │  Host port :8080      │  ← only published port
                       └───────────┬───────────┘
                                   │  docker port mapping
                                   ▼
 ┌─────────────────────────────────────────────────────────────────────────┐
 │  dotblog (docker bridge network — internal DNS via service names)       │
 │                                                                         │
 │                       ┌────────────────────┐                            │
 │                       │  gateway  :8080    │  Spring Cloud Gateway      │
 │                       └─────────┬──────────┘                            │
 │                                 │  route by path predicate              │
 │           ┌──────────┬──────────┼──────────┬──────────┐                 │
 │           ▼          ▼          ▼          ▼                            │
 │       ┌───────┐  ┌───────┐  ┌───────┐  ┌────────────┐                   │
 │       │ auth  │  │ user  │  │ blog  │  │ engagement │                   │
 │       │ :8081 │  │ :8082 │  │ :8083 │  │  :8084     │                   │
 │       └───────┘  └───┬───┘  └───┬───┘  └──────┬─────┘                   │
 │                      ▲          │              │                        │
 │                      └──────────┴──────────────┘  /internal/users/*    │
 │                                                                         │
 │                      ┌──────────┴──────────┐                            │
 │                      ▼                     ▼                            │
 │              ┌──────────────┐      ┌──────────────┐                     │
 │              │ media-service│      │   mongodb    │  or Atlas via      │
 │              │   :8085      │      │   :27017     │  DATABASE_URI      │
 │              └──────┬───────┘      └──────────────┘                     │
 │                     │                                                   │
 └─────────────────────┼───────────────────────────────────────────────────┘
                       │
                       ▼
                 Cloudinary (external)
                 
 auth-service → Resend (external email)
```

**Inter-service traffic (over docker DNS):**
- `blog` → `user` for author lookup and appending posts
- `engagement` → `user` for comment-author population
- `blog` and `user` → `media` for Cloudinary work

---

## External services (outside the docker network)

| Service | Used by | Purpose |
|---|---|---|
| **MongoDB Atlas** | auth, user, blog, engagement | Shared `users` and `blogs` collections. Selected via `DATABASE_URI` in `.env`. |
| **Cloudinary** | media-service only | Image hosting (uploads + asset destroy). Uses `CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`. |
| **Resend** | auth-service only | Transactional email (OTP, password reset). Uses `RESEND_API_KEY`. |

---

## Container inventory

Each container has a single `Dockerfile` (multi-stage, JRE 21 slim) and its own image tag.

| Container | Image | Port (host:container) | Purpose |
|---|---|---|---|
| `dotblog-gateway` | `dotblog/gateway:local` | `8080:8080` | Spring Cloud Gateway. Routes `/api/v2/*` to the right backend. The only container the browser ever talks to. |
| `dotblog-auth-service` | `dotblog/auth-service:local` | `8081:8081` | Register / login / JWT / OTP / forgot-reset. Sends email via Resend. |
| `dotblog-user-service` | `dotblog/user-service:local` | `8082:8082` | Profile reads/writes. Internal batch user-summary endpoint used by blog and engagement. |
| `dotblog-blog-service` | `dotblog/blog-service:local` | `8083:8083` | Blog CRUD, lists, publish, thumbnail upload (multipart in, delegates to media-service). |
| `dotblog-engagement-service` | `dotblog/engagement-service:local` | `8084:8084` | Likes / dislikes / comments — writes directly to the shared `blogs` collection. |
| `dotblog-media-service` | `dotblog/media-service:local` | `8085:8085` | Owns every Cloudinary call. Two internal endpoints: `POST /internal/upload` (multipart) and `POST /internal/delete`. |
| `dotblog-mongodb` | `mongo:7` | `27017:27017` | Fallback local Mongo. Only used when `.env` doesn't define `DATABASE_URI` (otherwise services connect to Atlas). |

---

## Who calls who

Inside docker, services reach each other by service name on the `dotblog` bridge network. The gateway is the only entrypoint exposed to your browser.

| Caller | Callee (URI) | Path | Why |
|---|---|---|---|
| Browser | `http://localhost:8080` | `/api/v2/*` | Single public endpoint. Gateway routes by path predicate. |
| gateway | `http://auth-service:8081` | `/register`, `/login`, `/verify`, `/resendOtp`, `/forgot-password`, `/reset-password`, `/isAuthenticated`, `/validate-password-reset-link/*` | Auth and session checks. |
| gateway | `http://user-service:8082` | `/Author/*`, `/update-about`, `/upload/profile-photo`, `/upload/cover-photo` | Profile reads + writes. |
| gateway | `http://blog-service:8083` | `/create-blog-save`, `/get-blog/*`, `/update-blog/*`, `/get-user-blogs`, `/get-blogs`, `/get-all-blogs`, `/get-categories-blogs/*`, `/publish-blog`, `/upload-thumbnail` | Blog flows. |
| gateway | `http://engagement-service:8084` | `/like-post`, `/dislike-post`, `/add-commnet`, `/delete-comment` | Engagement actions. |
| blog-service | `http://user-service:8082` | `POST /internal/users/{id}/posts`, `POST /internal/users/summary` | Append created blog id to the user; batch-lookup author summaries for list endpoints. |
| engagement-service | `http://user-service:8082` | `POST /internal/users/summary` | Populate comment authors (matches Node's mongoose populate). |
| blog-service | `http://media-service:8085` | `POST /internal/upload` (multipart), `POST /internal/delete` | Upload thumbnails; destroy the previous one on `update-blog` with `isThumbnailUpdated=true`. |
| user-service | `http://media-service:8085` | `POST /internal/delete` | Destroy old profile/cover photo when a new one is set. |
| All four backend services | MongoDB Atlas (or local `mongodb:27017`) | TCP 27017 | Selected via `DATABASE_URI` env var. |
| auth-service | Resend API | HTTPS | Send OTP / reset-link emails. |
| media-service | Cloudinary API | HTTPS | Upload / destroy images. |

---

## Lifecycle: publishing a blog with a new thumbnail

Traces one realistic request through every container it touches.

1. **Browser uploads thumbnail** — `POST /api/v2/upload-thumbnail`, multipart body `thumbnail_Img=<file>` and `PrevImage={public_id}`. Hits `localhost:8080`.
2. **Gateway forwards to blog-service** — Path predicate matches; gateway proxies to `http://blog-service:8083` over the docker network. JWT verified inside `blog-service` using the shared `JWT_SECRET`.
3. **blog-service streams the file to media-service** — `MediaClient` POSTs the multipart to `http://media-service:8085/internal/upload` with `folder=Dot-Blog/Blog_Thumbnails`.
4. **media-service uploads to Cloudinary** — Uses the Cloudinary Java SDK with the creds from `CLOUD_NAME` / `CLOUDINARY_API_KEY` / `CLOUDINARY_API_SECRET`. Returns `{ImageUrl, public_id}`.
5. **blog-service fires-and-forgets a delete for the old asset** — If `PrevImage.public_id` was sent, blog-service POSTs `/internal/delete` on media-service. Failures are swallowed so the user request never blocks on Cloudinary blips.
6. **Client calls update-blog with the new url + public_id** — `POST /api/v2/update-blog/{id}` — blog-service writes Title/Body/Thumbnail to Mongo via `MongoTemplate` (`$set`, not full replace, to preserve other fields).
7. **Client calls publish-blog** — `POST /api/v2/publish-blog` with `{Blogid}` — blog-service sets `isPublished=true` and `PublishedDate=now`.
8. **get-all-blogs returns the blog with populated author** — `GET /api/v2/get-all-blogs` — blog-service queries Mongo, collects author ids, and calls `user-service /internal/users/summary` once for the whole page; merges authors back into the response.

---

## Dockerfile structure

Every service uses the same multi-stage shape so images stay small and reproducible.

### Stage 1 — build (JDK 21 alpine)

Each service's `Dockerfile` copies `mvnw`, `.mvn`, the parent `pom.xml`, and **every sibling module** (`shared`, `gateway`, `auth-service`, `user-service`, `blog-service`, `engagement-service`, `media-service`). The parent POM validates that every declared module exists on disk before any `-pl` filter applies.

Then it runs:

```bash
./mvnw -B -pl <service> -am clean package -DskipTests
```

`-pl <service>` selects the target module; `-am` ("also-make") additionally builds anything that module depends on (e.g. the `shared/api-contract` jar). So only that service and its dependencies actually compile.

### Stage 2 — runtime (JRE 21 alpine)

Slim JRE image, non-root `spring` user, just the fat jar copied from the build stage as `/app/app.jar`. The container's `ENTRYPOINT` is `java -jar /app/app.jar`.

Final images are small and immutable. Rebuild a single service with:

```bash
docker compose build <service>
docker compose up -d --no-deps <service>
```

---

## Environment variables

The `.env` file at `dot-blog-backend/.env` feeds every container via `env_file`. Each service only needs the subset it actually uses.

| Variable | auth | user | blog | engage | media | gateway | Notes |
|---|:-:|:-:|:-:|:-:|:-:|:-:|---|
| `DATABASE_URI` | ✓ | ✓ | ✓ | ✓ | — | — | Atlas `mongodb+srv` URI or local `mongodb://mongodb:27017/...` |
| `JWT_SECRET` | ✓ | ✓ | ✓ | ✓ | — | — | Shared so any service can verify any token. |
| `CLOUD_NAME` | — | — | — | — | ✓ | — | Cloudinary only on media-service. |
| `CLOUDINARY_API_KEY` | — | — | — | — | ✓ | — | |
| `CLOUDINARY_API_SECRET` | — | — | — | — | ✓ | — | |
| `RESEND_API_KEY` | ✓ | — | — | — | — | — | Email provider; auth-service only. |
| `FRONTEND_URL` | ✓ | — | — | — | — | — | Used to build reset-password links. |
| `MEDIA_SERVICE_URI` | — | ✓ | ✓ | — | — | — | Defaults to `http://media-service:8085` in compose. |
| `USER_SERVICE_URI` | — | — | ✓ | ✓ | — | ✓ | blog + engagement use it for population; gateway uses it for routing. |
| `AUTH_SERVICE_URI` | — | — | — | — | — | ✓ | Gateway routing only. |
| `BLOG_SERVICE_URI` | — | — | — | — | — | ✓ | Gateway routing only. |
| `ENGAGEMENT_SERVICE_URI` | — | — | — | — | — | ✓ | Gateway routing only. |

---

## Startup order and healthchecks

`depends_on` + `healthchecks` make sure dependent services don't start until their dependencies are ready.

> Compose won't start a service whose dependency hasn't passed its `healthcheck` (when the condition is `service_healthy`) or hasn't at least entered `running` (`service_started`). Every Java service exposes `/actuator/health`; mongo uses `mongosh ping`.

| Wave | Service | Waits for | Health probe |
|:-:|---|---|---|
| 1 | `mongodb` | — | `mongosh db.adminCommand('ping')` |
| 2 | `media-service` | — | `GET /actuator/health` on `:8085` |
| 3 | `auth-service` | mongodb (healthy) | `GET /actuator/health` on `:8081` |
| 3 | `user-service` | mongodb (healthy), media-service (started) | `GET /actuator/health` on `:8082` |
| 4 | `blog-service` | mongodb (healthy), user-service (started), media-service (started) | `GET /actuator/health` on `:8083` |
| 4 | `engagement-service` | mongodb (healthy), user-service (started) | `GET /actuator/health` on `:8084` |
| 5 | `gateway` | auth, user, blog, engagement (all started) | `GET /actuator/health` on `:8080` |

---

## Persistence

Only `mongodb` has persistent state — everything else is replaceable.

### `mongodb_data` (named volume)

Mounted at `/data/db` inside the `mongodb` container. Survives `docker compose down`. `docker compose down -v` wipes it.

Only relevant when you use the local mongo fallback (no `DATABASE_URI` in `.env`). With Atlas, data lives upstream.

### Everything else is stateless

All five Java containers can be killed and recreated freely — no on-disk state, no local file cache. Uploaded images live in Cloudinary; sessions are pure JWT.

---

## Day-to-day commands

Run all commands from `dot-blog-backend/`.

| Action | Command | Note |
|---|---|---|
| Start everything (build if needed) | `docker compose up -d --build` | First boot from scratch. |
| Start everything (reuse cached images) | `docker compose up -d` | Daily-use boot after a `docker compose down`. |
| Show container status | `docker compose ps` | Quick sanity check — every row should say `(healthy)`. |
| Rebuild and bounce one service | `docker compose build blog-service && docker compose up -d --no-deps blog-service` | Fastest dev loop after changing one service. |
| Tail logs | `docker compose logs -f gateway blog-service` | Multi-service tailing. |
| Stop containers but keep data | `docker compose down` | Keeps the `mongodb_data` volume. |
| Full reset | `docker compose down -v` | Also drops the mongo volume — only when you really want a clean slate. |
| Exec into a container | `docker compose exec blog-service sh` | Alpine — `sh`, not `bash`. |
