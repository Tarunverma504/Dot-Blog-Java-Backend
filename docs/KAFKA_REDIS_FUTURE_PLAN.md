# Kafka & Redis — Future Integration Plan

Forward-looking plan for adding **Redis** (cache + ephemeral state) and **Kafka** (async event bus) to the Dot-Blog Java microservices backend.

> Status: **not implemented yet**. Current stack is MongoDB + Spring Boot synchronous REST (see `DOCKER_ARCHITECTURE.md`). This document lists the concrete places each tool fits in the existing code, the order to roll them out, and what changes per service.

---

## TL;DR

| Goal | Tool | Service(s) touched | Effort | Priority |
|---|---|---|---|---|
| OTP storage with TTL | Redis | `auth-service` | S | P0 |
| Password-reset token TTL | Redis | `auth-service` | S | P0 |
| Async OTP / reset email send | Kafka | `auth-service` + new `notification-service` | M | P0 |
| `UserSummary` cache for list pages | Redis | `blog-service`, `engagement-service` | S | P1 |
| Hot blog cache (`getAllPublished`, `getBlog`) | Redis | `blog-service` | M | P1 |
| Rate limiting on auth + engagement | Redis | `gateway` | M | P1 |
| `BlogPublishedEvent` fan-out | Kafka | `blog-service` + consumers | M | P2 |
| Decouple engagement → blog DB coupling | Kafka | `engagement-service`, `blog-service` | L | P2 |
| Like counter (atomic) | Redis | `engagement-service` | M | P2 |
| JWT revocation list | Redis | `auth-service`, `gateway` | M | P3 |
| Async media cleanup | Kafka | `blog-service`, `media-service` | S | P3 |
| `BlogLikedEvent` / `BlogCommentedEvent` | Kafka | `engagement-service` + consumers | M | P3 |

**S = ~0.5 day, M = 1–2 days, L = 3+ days.**

---

## Why we already have hooks for this

`dot-blog-backend/shared/events/` already defines `SendOtpEvent` and `UserVerifiedEvent` as Jackson-friendly records — so the original architecture intent was async messaging. We just never wired the broker in. Both records are the natural first Kafka payloads.

---

## Phase 0 — Add infra (one-time)

### `docker-compose.yml` additions

```yaml
  redis:
    image: redis:7-alpine
    container_name: dotblog-redis
    restart: unless-stopped
    ports: ["6379:6379"]
    networks: [dotblog]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  kafka:
    image: bitnami/kafka:3.7
    container_name: dotblog-kafka
    restart: unless-stopped
    ports: ["9092:9092"]
    environment:
      KAFKA_CFG_NODE_ID: "0"
      KAFKA_CFG_PROCESS_ROLES: "controller,broker"
      KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: "0@kafka:9093"
      KAFKA_CFG_LISTENERS: "PLAINTEXT://:9092,CONTROLLER://:9093"
      KAFKA_CFG_ADVERTISED_LISTENERS: "PLAINTEXT://kafka:9092"
      KAFKA_CFG_CONTROLLER_LISTENER_NAMES: "CONTROLLER"
      KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP: "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT"
      ALLOW_PLAINTEXT_LISTENER: "yes"
    networks: [dotblog]

  kafka-ui:    # optional, dev only
    image: provectuslabs/kafka-ui:latest
    container_name: dotblog-kafka-ui
    ports: ["8090:8080"]
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092
    depends_on: [kafka]
    networks: [dotblog]
```

### Shared env vars (`.env`)

```
REDIS_HOST=redis
REDIS_PORT=6379
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

### Shared Maven dependencies

Add to the relevant service POMs only when each phase ships — don't bulk-add:

```xml
<!-- Redis -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Kafka -->
<dependency>
  <groupId>org.springframework.kafka</groupId>
  <artifactId>spring-kafka</artifactId>
</dependency>
```

---

## Redis plan

### R1 — OTP storage with TTL (`auth-service`, P0)

**Today:** OTPs are written to the `User` document in `AuthService.register()` and `resendOtp()`, and validated in `verify()`. Stale OTPs sit in Mongo forever; expiry is manual.

**Change:**
- New `OtpStore` bean wrapping `StringRedisTemplate`.
  - `save(userId, otp)` → `SETEX otp:{userId} 600 {otp}` (10 min).
  - `get(userId)` → `GET otp:{userId}`.
  - `delete(userId)` → `DEL otp:{userId}` on successful verify.
- Remove `otp` field from `User` document (data migration: drop column on next deploy).
- `verify()` reads from `OtpStore` instead of `User.getOtp()`.

**Test:** existing auth Testcontainers test + new Redis Testcontainer for `OtpStore`.

---

### R2 — Password-reset tokens with TTL (`auth-service`, P0)

**Today:** entire `ForgotPassword` Mongo collection with an `expired` boolean managed in `AuthService.forgotPassword()` / `resetPassword()` / `validateResetLink()`.

**Change:**
- `ResetTokenStore`:
  - `issue(userId, email)` → `SETEX reset:{uuid} 1800 {userId}|{email}` (30 min), returns the uuid used in the link.
  - `consume(uuid)` → `GETDEL reset:{uuid}` (atomic single-use).
- Delete the `ForgotPassword` repository, domain, and collection.
- `frontendUrl + "/reset-password/" + uuid` link format stays identical, so no frontend changes.

---

### R3 — `UserSummary` cache (`blog-service` + `engagement-service`, P1)

**Today:** every list-page hits `user-service` over HTTP via `UserClient.summariesByIds(ids)` (called from `BlogService.populateAuthors`, `BlogService.populateComments`, `EngagementService.populateAndSort`). Big N+1 across services.

**Change:**
- Wrap `UserClient.summariesByIds(...)` with a Redis read-through:
  - Key: `user:summary:{userId}`, value: serialized `UserSummary`, TTL 300s.
  - Multi-get with `mget`; fetch the misses from `user-service`, `mset` results.
- Cache invalidation: `user-service` publishes `UserProfileUpdatedEvent` on profile/photo change → `blog-service` / `engagement-service` `DEL` the key. (If not on Kafka yet, just live with the TTL.)

**Win:** list endpoints become near-zero user-service traffic for the common "logged-in user reloads feed" case.

---

### R4 — Hot blog cache (`blog-service`, P1)

**Today:** `BlogService.getAllPublished`, `getByCategory`, `getBlog`, `getAllBlogsNoSort` always hit Mongo.

**Change:**
- Spring `@Cacheable` with `RedisCacheManager`.
  - `blogs:published:{searchHash}` → 60s TTL.
  - `blogs:category:{name}` → 60s TTL.
  - `blogs:detail:{id}` → 120s TTL (vary by `optionalUserId` for `isAlreadyLiked`, or keep that field out of cache and compute post-hit).
- Invalidate on `publishBlog`, `updateBlog`, `createBlog`, and on like/comment events (see K3).

**Caveat:** `isAlreadyLiked` is per-user; either skip it from the cached payload and compute on the way out, or cache only the public projection.

---

### R5 — Rate limiting at the gateway (`gateway`, P1)

**Targets:** `/api/v2/register`, `/login`, `/forgot-password`, `/resend-otp`, `/like`, `/dislike`, `/add-comment`.

**Change:**
- Use Spring Cloud Gateway's built-in `RequestRateLimiter` filter with `redis-rate-limiter`.
- Key resolver: IP for unauthenticated routes, userId from JWT for authenticated routes.
- Limits (tune later): 5/min on auth endpoints, 30/min on engagement endpoints.

---

### R6 — Like counter (`engagement-service`, P2)

**Today:** `EngagementService.likePost` / `dislikePost` read the whole blog doc, scan the `likes` sub-array, then push/pull and re-read.

**Change:**
- Source of truth in Redis:
  - `SADD likes:{blogId} {userId}` / `SREM likes:{blogId} {userId}`
  - `SCARD likes:{blogId}` for count.
  - `SISMEMBER likes:{blogId} {userId}` for `isAlreadyLiked` (replaces the array scan in `BlogService.getBlog`).
- Background flush: every 30s or on `BlogLikedEvent` consume, batch-write counts back to Mongo so list pages and analytics still work without Redis.

---

### R7 — JWT revocation (`auth-service` + `gateway`, P3)

**Why:** today `isAuthenticated()` only validates signature + expiry. There's no "log out everywhere" or "invalidate on password change."

**Change:**
- On logout-all / password-change: `SETEX jwt:revoked:{jti} {ttlOfToken} 1`.
- Gateway pre-filter: reject if `EXISTS jwt:revoked:{jti}`.
- Requires adding a `jti` claim in `JwtService.createToken`.

---

## Kafka plan

### Topic conventions

- Name: `dotblog.<domain>.<event>` (e.g. `dotblog.auth.otp-requested`).
- Partitions: 3 (dev), 6+ (prod).
- Keys: aggregate id (`userId` for auth/user events, `blogId` for blog/engagement).
- Payloads: JSON of records in `shared/events`.
- Compatibility: only add fields, never remove or rename — Jackson `@JsonProperty` already in place.

### K1 — `SendOtpEvent` → async email (P0)

**Today:** `AuthService.register` / `resendOtp` / `forgotPassword` call `emailService.sendOtp(...)` / `sendResetLink(...)` synchronously. Resend latency or downtime blocks the user.

**Change:**
- Producer: `auth-service` publishes `SendOtpEvent` to `dotblog.auth.otp-requested` after the user is saved. HTTP returns immediately.
- Consumer: new `notification-service` (or reuse `auth-service` as a transitional consumer) listens on `dotblog.auth.otp-requested`, calls Resend, retries with backoff, dead-letters to `dotblog.auth.otp-requested.DLT`.
- Same pattern for a `SendResetLinkEvent` (add to `shared/events`).

**Acceptance:** registration succeeds with Resend stopped; email is delivered when Resend recovers.

---

### K2 — `UserVerifiedEvent` fan-out (P1)

**Today:** `AuthService.verify` just flips `verified = true`. Nothing else reacts.

**Change:**
- Publish `UserVerifiedEvent` on successful verify.
- Initial consumers:
  - `user-service` → pre-create empty profile so first profile page load is instant.
  - `notification-service` → "welcome" email.
- Future: analytics, marketing pipeline.

---

### K3 — `BlogPublishedEvent` (P2)

**Trigger:** `BlogService.publishBlog`.

**Payload:** `blogId`, `userId`, `title`, `category`, `publishedAt`.

**Consumers (future-ready, none required now):**
- Cache invalidator → `DEL blogs:published:*` and `DEL blogs:category:{cat}`.
- Search indexer (Meilisearch / OpenSearch).
- Notifications service → fan-out to followers (once that feature exists).
- Sitemap / RSS generator.

---

### K4 — Decouple engagement from blog DB (P2, design fix)

**Today (acknowledged smell):**
```text
engagement-service writes directly into the `blogs` collection
owned by blog-service. See the class-level comment on EngagementService.
```

**Target architecture:**
- `engagement-service` owns its own collection (`engagements` or `likes` + `comments`).
- It emits `BlogLikedEvent`, `BlogUnlikedEvent`, `BlogCommentedEvent`, `BlogCommentDeletedEvent`.
- `blog-service` keeps a **projection** (`likesCount`, `commentsCount`, plus a short "recent comments" snapshot) by consuming those events.
- List endpoints in `blog-service` keep working without any cross-service calls.

**Migration steps:**
1. Add events + consumers (project counts back into `blogs`) **while keeping the direct write** — dual-write phase.
2. Backfill the new `engagements` collection from existing `blogs.likes/comments`.
3. Cut over reads in `EngagementService` to the new collection.
4. Remove the direct writes from `engagement-service` to `blogs`. Done.

---

### K5 — Async media cleanup (P3)

**Today:** `BlogService.updateBlog` synchronously calls `mediaClient.delete(prevPublicIdToDelete)` to drop orphaned Cloudinary thumbnails. Cloudinary latency leaks into the user's update request.

**Change:**
- `blog-service` publishes `MediaDeletionRequestedEvent { publicId }` after the DB write.
- `media-service` consumes and deletes, with retry + DLT.

---

## Suggested rollout order

1. **Phase A (P0, ~1 sprint):**
   - Add Redis + Kafka to compose.
   - R1 OTP TTL, R2 reset-token TTL.
   - K1 async OTP / reset email (stand up `notification-service` as a thin consumer).
2. **Phase B (P1):**
   - R3 user-summary cache.
   - R5 gateway rate limiting.
   - K2 `UserVerifiedEvent` consumers.
   - R4 hot blog cache.
3. **Phase C (P2):**
   - K3 `BlogPublishedEvent` + cache invalidation.
   - K4 engagement decoupling (dual-write → projection → cutover).
   - R6 like counter in Redis.
4. **Phase D (P3, nice-to-have):**
   - R7 JWT revocation.
   - K5 async media cleanup.

---

## Operational notes

- **Local-only deps:** keep Redis/Kafka opt-in via Spring profiles (`redis`, `kafka`) until Phase A ships, so contributors without the broker can still run services.
- **Testcontainers:** use `RedisContainer` and `KafkaContainer` for integration tests. The existing auth integration test is a good template.
- **Outbox pattern (when stronger guarantees are needed):** for events that must not be lost on producer crash (K3, K4), write an `outbox` document inside the same Mongo transaction as the business write, and ship it to Kafka with a Debezium-style relay or a simple scheduled flusher. Not required for K1 (an OTP that never sent can be re-requested).
- **Monitoring:** expose Micrometer Kafka + Lettuce metrics, scrape with whatever you wire up later (Prometheus is the obvious pick).
- **Schema discipline:** every event payload lives in `shared/events` as a Jackson record. Treat it as a public API.

---

## What this plan deliberately does NOT do

- No CQRS / event sourcing — projections only.
- No saga orchestrator — current cross-service writes are small enough for choreographed events + compensating actions (see how `BlogService.createBlog` already rolls back on `userClient.appendPost` failure).
- No Redis as the primary datastore — Mongo stays canonical for everything except OTP and reset tokens.
- No Kafka Streams / ksqlDB — overkill at this scale.
