# Kafka Hands-On Mentorship — Dot-Blog Backend

A guided, exercise-based course that takes you from **"never touched Kafka"** to **"can defend event-driven architecture decisions in a senior-level interview"** — using this repo as your lab.

> This is a **companion** to [`KAFKA_REDIS_FUTURE_PLAN.md`](./KAFKA_REDIS_FUTURE_PLAN.md). That doc is the architectural roadmap. **This** doc is the day-by-day workout plan.

---

## 1. Why this course + how it benefits you

You are not learning Kafka in isolation — you are grafting it onto a **real 6-service Spring Boot backend that already has real problems Kafka solves**. That matters because:

| Benefit | What it looks like in practice |
|---|---|
| **Portfolio piece** | Every phase ends with a working PR-sized diff you can push to GitHub. Recruiters and hiring managers can `git log` and see genuine event-driven work. |
| **Interview stories (STAR format)** | Each exercise ends with a **"Tell me about a time..."** prompt. By the end you will have 12+ rehearsed stories covering producer/consumer, DLTs, outbox, idempotency, ordering, rebalancing, and CQRS-lite. |
| **Muscle memory, not tutorial memory** | You write the code. I ask the questions. This is the opposite of watching a YouTube playlist — you cannot skip understanding. |
| **Real-world scars** | You will intentionally break things (kill the broker mid-transaction, send a poison message, cause a rebalance storm) so you can talk about how it *actually* fails, not how the docs say it fails. |
| **Distributed-systems intuition** | Kafka is the excuse; the real prize is understanding eventual consistency, at-least-once semantics, backpressure, and the dual-write problem. These transfer to every future job. |
| **Systems-design fluency** | You will refactor a tight-DB-coupling anti-pattern (engagement-service writing into blog-service's collection) into a proper materialized projection. That single diff is a whiteboard-worthy story. |

**Estimated total effort:** 30–45 hours of focused work, spread over 3–6 weeks.

---

## 2. How to use this document (the mentor–student contract)

- **You** = the student. You write all production code, config, tests.
- **Me** = the mentor. I set the exercise, ask questions *before* you code (to make you think, not just type), review your work, and unlock the next step.
- Each exercise has this shape:

  ```
  ### Exercise X.Y — <name>
  🎯 Learning objective
  🧠 Prework question(s)   ← You answer these to me before writing code
  🛠️ Task                  ← What to build
  ✅ Checkpoint             ← How you (and I) know it works
  ⚠️ Gotchas                ← Things I want you to hit and understand
  🎤 Interview vault        ← Q&A + STAR prompt to add to your notes
  ```

- **Never skip the prework question.** If you can't answer it, that IS the lesson — tell me you can't and we discuss.
- **After every checkpoint, ping me in chat.** I'll review, ask a follow-up, then unlock the next exercise.
- Track your progress in the checklist at the bottom (Section 15).

---

## 3. Prerequisites & environment check

You should already have (this repo confirms most):

- [x] Java 21, Maven, Docker + Compose — this repo runs
- [x] `docker compose up -d` brings up 6 services + MongoDB successfully
- [x] `./scripts/smoke.sh` passes
- [ ] `kcat` (formerly `kafkacat`) installed for CLI experiments — `sudo apt install kcat`
- [ ] Comfortable reading Spring Boot service classes and `pom.xml` — YES (you built this repo)
- [ ] Familiar with `KafkaTemplate` / `@KafkaListener` — **NO, we'll fix that in Phase 0**

---

## 4. Curriculum at a glance

| Phase | Theme | Real bug/feature we fix | Kafka concepts unlocked |
|-------|-------|-------------------------|-------------------------|
| **0** | Foundations | Nothing yet — just Kafka in a box | Broker, topic, partition, offset, consumer group, KRaft |
| **1** | First real event | `forgotPassword` no longer 504s when Resend is down; `register` finally sends OTP | Producer, `@KafkaListener`, JSON serdes, DLT, retries |
| **2** | Fan-out | `UserVerifiedEvent` triggers welcome email AND profile pre-creation | Consumer groups, broadcast vs load-balance, replay |
| **3** | Keys & ordering | `BlogPublishedEvent` for future search/feed/notifications | Partition keys, ordering guarantees, schema evolution |
| **4** | Retry & DLT deep dive | Async orphan-image cleanup on Cloudinary | `@RetryableTopic`, poison messages, exponential backoff |
| **5** | Materialized projections | Break engagement-service's cross-service DB write | Event-sourced projection, dual-write phase, cutover |
| **6** | Outbox pattern | Replace `createBlog`'s try/rollback with reliable event | Dual-write problem, transactional outbox, idempotent consumer |
| **7** | Ops & interview polish | Testcontainers, monitoring, chaos | Consumer lag, rebalance, static membership, testing patterns |

---

## 5. Kafka concept map (read once, refer back forever)

```
                     ┌──────────────────────────────────┐
                     │            KAFKA BROKER          │
                     │  ┌──────────────────────────┐    │
                     │  │  TOPIC: dotblog.auth.otp │    │
                     │  │  ┌───┐ ┌───┐ ┌───┐       │    │
                     │  │  │ P0│ │ P1│ │ P2│       │    │
                     │  │  └───┘ └───┘ └───┘       │    │
                     │  └──────────────────────────┘    │
                     └──────────────────────────────────┘
                              ▲                 │
                    produce   │                 │  consume
                              │                 ▼
                    ┌─────────────────┐   ┌─────────────────┐
                    │  auth-service   │   │ notification-svc│
                    │   (producer)    │   │   (consumer)    │
                    └─────────────────┘   └─────────────────┘
```

- **Broker** = server that stores messages
- **Topic** = named log of messages
- **Partition** = one shard of a topic; ORDERING only guaranteed within a partition
- **Offset** = position in a partition (monotonically increasing integer)
- **Producer** = writes messages
- **Consumer** = reads messages, remembers its offset
- **Consumer group** = a set of consumers that split partitions among themselves (load balance). Two DIFFERENT groups both see EVERY message (fan-out).
- **Key** = optional string on each message; same key → same partition → order preserved
- **DLT (Dead Letter Topic)** = where failed messages go after retries exhaust
- **Offset commit** = telling the broker "I successfully processed up to here"

If any of these confuses you at any point, STOP and ask me.

---

## 6. Phase 0 — Foundations

Goal: end this phase with a running Kafka broker, a UI to peek at topics, and one message flowing end-to-end from an isolated test — **no business code touched yet**.

### Exercise 0.1 — Mental model check-in

🎯 **Objective:** confirm you understand the core vocabulary before writing config.

🧠 **Prework question (answer in chat):**
1. In your own words: what is the difference between a **topic** and a **partition**?
2. If two consumers are in the **same consumer group**, do they both receive every message? What if they are in **different groups**?
3. Kafka guarantees message ordering. What is the *scope* of that guarantee?

🛠️ **Task:** none — just answer.

✅ **Checkpoint:** you send me your three answers.

🎤 **Interview vault:** every one of these three is a top-10 Kafka interview question. Save your answers verbatim.

---

### Exercise 0.2 — Bring the broker up

🎯 **Objective:** Kafka + Kafka-UI running under `docker compose`, alongside your existing services.

🧠 **Prework question:**
- What is **KRaft mode** and why don't we need Zookeeper anymore?

🛠️ **Task:**
1. In `docker-compose.yml`, add two new services **without breaking existing ones**:
   - `kafka` using image `bitnami/kafka:3.7`, KRaft single-node, port `9092` on host and network.
   - `kafka-ui` using `provectuslabs/kafka-ui:latest`, port `8090`, pointed at `kafka:9092`.
2. Add both to the existing `dotblog` network.
3. Add a `healthcheck` to `kafka` that runs `kafka-topics.sh --bootstrap-server localhost:9092 --list`.
4. Copy the compose snippet from `KAFKA_REDIS_FUTURE_PLAN.md` §Phase 0 as a starting point, but understand every env var before pasting.

✅ **Checkpoint:**
- `docker compose up -d kafka kafka-ui` succeeds.
- `docker compose ps` shows `dotblog-kafka` as **healthy**.
- You can open `http://localhost:8090` in a browser and see one broker, zero topics.
- All existing services still start and `./scripts/smoke.sh` still passes.

⚠️ **Gotchas:**
- If `KAFKA_CFG_ADVERTISED_LISTENERS` is wrong, containers can reach the broker but your host laptop can't (or vice versa). You may need both `PLAINTEXT://kafka:9092` (internal) AND `EXTERNAL://localhost:29092` (host).
- KRaft cluster needs a `CLUSTER_ID` — bitnami generates one for you; just be aware.

🎤 **Interview vault:**
- *"Why KRaft over Zookeeper?"* — write the 2-sentence answer.
- *"What advertised listeners are and why they trip up every first-time Kafka user."*

---

### Exercise 0.3 — Hello-world producer + consumer (CLI only, no code yet)

🎯 **Objective:** experience produce → store → consume with zero Java.

🧠 **Prework question:**
- When a producer sends a message *without* a key, how does Kafka decide which partition it lands in?

🛠️ **Task:**
1. Using `docker exec` into the `kafka` container (or the Kafka-UI), create a topic `dotblog.playground` with 3 partitions.
2. Open two terminals:
   - **Terminal A** — producer: `kafka-console-producer.sh --broker-list kafka:9092 --topic dotblog.playground`
   - **Terminal B** — consumer: `kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic dotblog.playground --from-beginning --group demo-group-1`
3. Type 5 messages in A. Observe them appear in B.
4. Start a **second consumer** in a third terminal with the **same** `--group demo-group-1`. Send 10 more messages. Observe the split.
5. Start a **third consumer** with a **DIFFERENT** `--group demo-group-2`. Send 5 more. Observe it gets **all** of them.

✅ **Checkpoint:** send me a note describing what you observed in steps 4 and 5, in your own words.

🎤 **Interview vault:** *"Explain how consumer groups let Kafka do both point-to-point (queue) and pub-sub (topic) semantics."*

---

## 7. Phase 1 — First real event: async OTP email

Goal: fix a real bug (`AuthService.register()` never emails the OTP) and remove a real availability issue (`forgotPassword` returns 504 if Resend is down), using Kafka.

**Architecture we're building in this phase:**

```
 [Client] --POST /register--> [auth-service] --produce SendOtpEvent--> [Kafka]
                                     │
                                     └── returns 201 immediately (no email inline)

 [Kafka topic dotblog.auth.otp-requested] --> [notification-service consumer] --> [Resend API]
                                                          │
                                                          └── on failure: retry, then DLT
```

### Exercise 1.1 — Design the event

🎯 **Objective:** treat the event schema as a public API.

🧠 **Prework questions:**
1. Should `SendOtpEvent` contain the raw OTP string, or a reference to it (e.g., token id) that the consumer looks up? Trade-offs?
2. Should it contain the user's email, or just `userId`? What if user changes email between produce and consume?
3. What field would you add today knowing you might need to add another consumer later (e.g., SMS)?

🛠️ **Task:**
- Open `shared/events/src/main/java/com/dotblog/events/SendOtpEvent.java`.
- Extend it (or add sibling records `SendResetLinkEvent`) with fields you argued for above.
- Add `eventId` (UUID), `occurredAt` (Instant), and `channel` (enum: EMAIL, SMS placeholder).

✅ **Checkpoint:** show me your final record + one paragraph justifying every field.

⚠️ **Gotcha:** Jackson + Java records + Kafka JSON serdes have a quirk with default constructors. If a consumer can't deserialize your event later, come back and check `@JsonCreator`.

🎤 **Interview vault:** *"How do you design a Kafka event payload that won't paint you into a corner in 6 months?"* — schema evolution, additive-only, avoid PII where possible, use aggregate ids as keys.

---

### Exercise 1.2 — Producer in auth-service

🎯 **Objective:** wire `KafkaTemplate` into auth-service and produce your first real event.

🧠 **Prework question:** what does `spring.kafka.producer.acks=all` do, and why would you want it for OTP but *not* for high-throughput telemetry?

🛠️ **Task:**
1. Add `spring-kafka` to `auth-service/pom.xml`. Add `shared/events` as a dependency (it isn't yet — this is why it's orphaned today).
2. In `auth-service/src/main/resources/application.yml`, add `spring.kafka.*` config: bootstrap servers from env `KAFKA_BOOTSTRAP_SERVERS`, JSON serializer, `acks=all`, `retries=3`, `enable.idempotence=true`.
3. Add a `KafkaConfig` `@Configuration` class with a `KafkaTemplate<String, SendOtpEvent>` bean.
4. Add an `OtpEventPublisher` service with `publish(SendOtpEvent event)` that uses the template. Key = `userId`.
5. Do NOT call it from `AuthService` yet — Exercise 1.3.

✅ **Checkpoint:** unit-test-invoke the publisher from a temporary `@PostConstruct` or a scratch REST endpoint, verify the message appears in Kafka-UI on topic `dotblog.auth.otp-requested`.

⚠️ **Gotcha:** don't put credentials or the raw OTP into logs. Your Kafka appender will log the whole event body by default.

🎤 **Interview vault:** *"Walk me through `acks=0`, `acks=1`, `acks=all` — which one prevents data loss on broker failure, and at what latency cost?"*

---

### Exercise 1.3 — Refactor `forgotPassword` and `register` to publish, not block

🎯 **Objective:** eliminate the inline Resend call from the request thread.

🧠 **Prework question:**
- If you `kafkaTemplate.send(...)` inside `forgotPassword`, and the broker is down, what happens to the user's HTTP request? What are your three options?

🛠️ **Task:**
1. In `AuthService.forgotPassword()` (`auth-service/.../AuthService.java` L207), replace the direct `emailService.sendResetLink(...)` call with `otpEventPublisher.publish(new SendResetLinkEvent(...))`.
2. In `AuthService.register()` (L64), add the OTP email publish that was missing.
3. Return 202 (Accepted) instead of 200 where semantically appropriate.
4. Handle the "broker down" case: catch, log, and **still return success** OR return 503 — your call, but justify it.

✅ **Checkpoint:**
- `POST /api/v2/register` returns 201 in <100ms with Resend unreachable (you can prove this by pointing `RESEND_API_KEY` at a bogus value).
- A message appears in Kafka-UI on `dotblog.auth.otp-requested` for every registration.

🎤 **Interview vault:** *"Tell me about a time you decoupled a user-facing endpoint from a slow downstream dependency."* → STAR: latency went from 800ms p95 to 40ms p95; availability decoupled; trade-off accepted (email arrives async, user is told to check email).

---

### Exercise 1.4 — Build `notification-service` as consumer

🎯 **Objective:** create a new microservice whose entire job is to consume events and call Resend.

🧠 **Prework question:** why is it architecturally *better* to put the consumer in a new service instead of another `@KafkaListener` in auth-service?

🛠️ **Task:**
1. Add `notification-service` as a new Maven module (copy structure from `media-service` — smallest existing service).
2. Add it to root `pom.xml`, add a `Dockerfile`, add it to `docker-compose.yml`.
3. Dependencies: `spring-boot-starter`, `spring-kafka`, `shared/events`, `spring-web` (for Resend HTTP call). NO Mongo dependency — this service is stateless.
4. Add `OtpEventListener` with `@KafkaListener(topics="dotblog.auth.otp-requested", groupId="notification-service")`. On receive, call Resend.
5. Move the Resend HTTP code from `auth-service/EmailService` into this new service. Delete it from auth-service.

✅ **Checkpoint:**
- End-to-end: `POST /register` → email lands in inbox (or Resend dashboard shows delivery).
- `docker compose stop notification-service`; register a user; start notification-service; email still arrives (proves Kafka's persistence + replay).

⚠️ **Gotcha:** consumer starts before broker is fully up → connection refused → retries → eventual success. Add `depends_on: kafka: {condition: service_healthy}`.

🎤 **Interview vault:** *"Why do you have a dedicated notification-service? What responsibilities go there vs. in the domain services?"*

---

### Exercise 1.5 — Retries + Dead Letter Topic

🎯 **Objective:** handle Resend transient failures without dropping OTPs.

🧠 **Prework question:** what is the risk of infinite retries on a `@KafkaListener`? What kind of failure is a "retry can help" failure vs. a "retry will never help" failure?

🛠️ **Task:**
1. On `OtpEventListener`, add `@RetryableTopic(attempts="4", backoff=@Backoff(delay=1000, multiplier=2.0), dltStrategy=FAIL_ON_ERROR)`.
2. Prove it works: temporarily throw `new RuntimeException("boom")` in the listener 100% of the time. Send 3 OTPs.
3. Verify in Kafka-UI: retry topics `dotblog.auth.otp-requested-retry-0/1/2/3` all populate, then the messages land in `dotblog.auth.otp-requested-dlt`.
4. Add a `@KafkaListener` for the DLT that logs a WARN with the eventId — this is your operator alert hook.

✅ **Checkpoint:** show me screenshots of the retry + DLT topics populated.

⚠️ **Gotcha:** be careful — some errors should NOT retry (e.g., malformed payload). Configure `retryTopicHeaders` or use `DefaultErrorHandler` with `addNotRetryableExceptions`.

🎤 **Interview vault:** *"Design a retry-and-DLT strategy. When should you retry, when should you skip, and what do you do with dead letters at 3am?"*

---

### Exercise 1.6 — Idempotency drill

🎯 **Objective:** make the consumer safe against duplicate delivery.

🧠 **Prework question:** why can Kafka deliver the same message twice even with `acks=all`? (Hint: it involves consumer restarts.)

🛠️ **Task:**
1. Simulate a duplicate: manually re-send a message from Kafka-UI (or use kcat).
2. Observe the user receives TWO OTP emails.
3. Fix: in `notification-service`, keep a small in-memory `LinkedHashMap<eventId, timestamp>` (bounded to last 10k) and skip if seen. (In production this would be Redis with TTL — we'll use Redis in a later course.)
4. Retest: duplicate now emails only once.

✅ **Checkpoint:** show me the code + a log line proving the dedupe fired.

🎤 **Interview vault:** *"Kafka gives you at-least-once by default. What does that mean for your consumer code, and how do you achieve exactly-once *effect*?"*

---

## 8. Phase 2 — Consumer groups & fan-out

Goal: prove you understand consumer groups by making one event trigger two independent side effects.

### Exercise 2.1 — Produce `UserVerifiedEvent`

🛠️ **Task:** in `AuthService.verify()`, after flipping `verified=true`, publish `UserVerifiedEvent` to `dotblog.auth.user-verified`, keyed by `userId`.

✅ **Checkpoint:** message visible in Kafka-UI on registration → OTP → verify flow.

---

### Exercise 2.2 — Two independent consumers

🛠️ **Task:**
1. In `notification-service`, add a listener with `groupId="notification-service"` that sends a "welcome" email.
2. In `user-service`, add a listener with `groupId="user-service-profile-seed"` that creates an empty profile document if one doesn't exist.
3. Verify BOTH fire on one verify event.

✅ **Checkpoint:** logs from both services show handling of the same `eventId`.

---

### Exercise 2.3 — Consumer group experiment (the "aha" moment)

🧠 **Prework question:** predict — if I change both listeners to use `groupId="shared-group"`, what will happen?

🛠️ **Task:** actually do it. Send 10 verify events. Count how many welcome emails vs. how many profile seeds fire.

✅ **Checkpoint:** describe to me what happened and why — this is the exercise that makes consumer groups click permanently.

🎤 **Interview vault:** *"Walk me through what happens when I put two consumers in the same group vs. different groups"* — you now have a battle scar to reference.

---

## 9. Phase 3 — Keys, partitions, ordering (`BlogPublishedEvent`)

Goal: publish `BlogPublishedEvent` correctly keyed, and understand why key choice is a systems-design decision.

### Exercise 3.1 — Partition key thought experiment

🧠 **Prework questions:**
1. If I key `BlogPublishedEvent` by `userId`, what breaks? What works well?
2. If I key by `blogId`, what breaks? What works well?
3. If I key by `null` (round-robin), what breaks?

Answer all three in chat before touching code.

---

### Exercise 3.2 — Publish `BlogPublishedEvent`

🛠️ **Task:**
1. Add `BlogPublishedEvent { blogId, userId, title, category, publishedAt }` to `shared/events`.
2. Add `BlogEventPublisher` to blog-service.
3. Emit from `BlogService.publishBlog()` after the Mongo `$set`.
4. Choose your partition key based on your Ex 3.1 answers, and defend it to me.

✅ **Checkpoint:** publish 5 blogs from the same author, verify all 5 land in the same partition (Kafka-UI shows partition-per-message).

---

### Exercise 3.3 — Toy search-index consumer

🛠️ **Task:** in `notification-service` (yes, misusing the name — think of it as "cross-cutting consumers"), add a listener that maintains an in-memory `Map<blogId, {title, category}>` as a *materialized view*. Expose `GET /internal/search?q=xxx` that scans the map.

✅ **Checkpoint:** publish a blog, `GET /internal/search?q=<title-word>` returns it.

🎤 **Interview vault:** *"Have you ever built a materialized view from a Kafka topic?"* — you now have one.

---

### Exercise 3.4 — Schema evolution drill

🛠️ **Task:**
1. Add a `tags: List<String>` field to `BlogPublishedEvent`.
2. Deploy blog-service (producer) with the new schema.
3. Do NOT redeploy notification-service (old consumer).
4. Publish a blog with tags. Observe: does the old consumer crash, ignore the field, or blow up?

✅ **Checkpoint:** report to me what happened. If it crashed, that's the lesson — Jackson needs `@JsonIgnoreProperties(ignoreUnknown = true)`.

🎤 **Interview vault:** *"Tell me about schema evolution in a Kafka system you've worked on."*

---

## 10. Phase 4 — Retries & DLT deep dive (async media cleanup)

### Exercise 4.1 — Producer

🛠️ **Task:** replace the inline `mediaClient.delete(prevPublicIdToDelete)` in `BlogService.updateBlog()` and `ProfileService.updatePhoto()` with a producer to `dotblog.media.deletion-requested`.

### Exercise 4.2 — Consumer with tiered retry

🛠️ **Task:** in `media-service`, add `@RetryableTopic` with a **long** backoff (Cloudinary might be down for minutes), max 6 attempts, DLT strategy `FAIL_ON_ERROR`.

### Exercise 4.3 — Poison message drill

🛠️ **Task:** manually inject a message with an invalid publicId format. Verify:
1. Consumer keeps trying (bad!)
2. Fix: add `.notRetryOn(InvalidPublicIdException.class)` so poison messages skip retry.

🎤 **Interview vault:** *"What's a poison message and how do you handle one?"*

---

## 11. Phase 5 — Materialized projections (engagement decoupling) ⭐ centerpiece

This is the phase that will give you your **best** interview story. Take your time.

### Exercise 5.1 — Design new events + owned collection

🧠 **Prework:**
- Sketch on paper: what events do we need? (Like/Unlike/Comment/CommentDelete.)
- What does engagement-service's OWN Mongo collection look like?
- What projection does blog-service maintain on each event?

### Exercise 5.2 — Dual-write phase (safe migration)

🛠️ **Task:**
1. Keep engagement-service's existing direct writes to the `blogs` collection.
2. **Additionally**, emit `BlogLikedEvent` / `BlogUnlikedEvent` / `BlogCommentedEvent` / `BlogCommentDeletedEvent`.
3. In blog-service, add a listener that maintains `likesCount` / `commentsCount` on the blog document from events only.
4. Prove parity: after 20 likes, both the direct-write field and the projected field agree.

### Exercise 5.3 — Backfill

🛠️ **Task:** write a one-off script (Java `main` or CLI) that reads existing `blogs.likes[]` and populates a new `engagements` collection.

### Exercise 5.4 — Cutover

🛠️ **Task:**
1. Switch engagement-service reads to the new `engagements` collection.
2. Remove the direct writes into `blogs.likes`.
3. Blog-service's projection is now the sole source for counters.
4. Delete the code smell comment in `EngagementService.java` — you earned it.

🎤 **Interview vault (⭐ the big one):** *"Tell me about a time you refactored a tight coupling between microservices."* — STAR: two services shared a table, changed to event-sourced projection, ran in dual-write for 2 weeks, cut over with zero downtime.

---

## 12. Phase 6 — Outbox pattern (`BlogCreatedEvent`)

### Exercise 6.1 — Break the naive producer (chaos exercise)

🧠 **Prework question:** if `BlogService.createBlog` does `mongo.save(blog)` then `kafka.send(event)`, and the JVM crashes between those two lines, what's the state of the world?

🛠️ **Task:** actually reproduce it. Add `Runtime.getRuntime().halt(1)` between the two lines temporarily. Restart. Observe: blog exists in Mongo, no event ever fired, user's post list is inconsistent forever.

### Exercise 6.2 — Outbox collection

🛠️ **Task:**
1. Add `outbox_events` collection.
2. In `createBlog`, write both the blog document AND an outbox document in a **single Mongo transaction** (needs Mongo replicaset — Atlas has it; for local, use `mongodb://mongo:27017/?replicaSet=rs0` with `mongo1` in RS mode).

### Exercise 6.3 — Relay poller

🛠️ **Task:** add a `@Scheduled(fixedDelay=500)` job that polls unprocessed outbox rows, publishes to Kafka, marks as sent. Add a `sentAt` field for observability.

### Exercise 6.4 — Idempotent consumer

🛠️ **Task:** in user-service's listener for `BlogCreatedEvent`, use `$addToSet` (not `$push`) on `user.posts` so replays don't create duplicates.

🎤 **Interview vault:** *"Explain the dual-write problem and how the outbox pattern solves it."* — this is a staff-engineer-level answer that will blow interviewers away.

---

## 13. Phase 7 — Ops, testing, monitoring

### Exercise 7.1 — Testcontainers integration test

🛠️ **Task:** write an integration test in `notification-service` that spins up `KafkaContainer`, publishes an `SendOtpEvent`, and asserts a mock Resend client was called.

### Exercise 7.2 — Consumer lag monitoring

🛠️ **Task:** enable `spring.kafka.listener.observation-enabled=true`. Expose `/actuator/metrics/kafka.consumer.records.lag`. Slow the consumer artificially (Thread.sleep) and watch lag grow in Kafka-UI.

### Exercise 7.3 — Rebalance experiment

🛠️ **Task:**
1. Scale notification-service to 3 replicas: `docker compose up -d --scale notification-service=3`.
2. Send 100 events. Observe partitions get distributed.
3. Kill one replica. Watch the rebalance in logs. Time how long consumers are paused.
4. Enable cooperative rebalancing (`partition.assignment.strategy=CooperativeStickyAssignor`). Repeat. Note the difference.

🎤 **Interview vault:** *"What is a Kafka rebalance and how do you minimize its impact?"*

---

## 14. Interview prep vault (fill this in as you go)

Keep this in your notes. After each exercise, add:

- **Q&A** — copy the "Interview vault" question and write your best answer.
- **STAR story** — Situation, Task, Action, Result. 60 seconds spoken.
- **Diagram** — sketch the before/after architecture on paper.

Suggested 20 canonical Kafka interview questions to master by end of course:

1. Topic vs. partition vs. offset — explain the mental model.
2. Producer `acks` levels — what does each guarantee?
3. Consumer groups — point-to-point vs. pub-sub in one system.
4. Ordering guarantees — scope and how key choice affects them.
5. `enable.idempotence=true` — what problem does it solve on the producer?
6. At-most-once, at-least-once, exactly-once — what does Kafka default to and why?
7. What is a rebalance and what triggers it?
8. Static membership vs. cooperative rebalancing.
9. Retry topics + DLT — design the flow.
10. Poison message — what is it, how do you handle it?
11. Outbox pattern — what problem, what solution.
12. Dual-write problem in your own words.
13. Idempotent consumer — how do you build one?
14. Schema evolution strategy — additive-only, contract testing, registry.
15. When would you NOT use Kafka? (Sync request/response, tiny event volume, need transactions across services.)
16. Kafka vs. RabbitMQ vs. SQS — one-line elevator for each.
17. Consumer lag — what is it, how do you monitor and alarm on it?
18. Compacted topics — when and why?
19. Materialized views / CQRS-lite — you built one in Phase 3 and 5.
20. Testing strategy — Testcontainers, embedded Kafka, contract tests.

---

## 15. Progress tracker

Copy this into a personal note or check items off as PRs merge.

- [ ] **Phase 0 — Foundations**
  - [ ] 0.1 Mental model check-in
  - [ ] 0.2 Broker up
  - [ ] 0.3 Hello-world CLI producer/consumer
- [ ] **Phase 1 — Async OTP email**
  - [ ] 1.1 Design the event
  - [ ] 1.2 Producer wired in auth-service
  - [ ] 1.3 `forgotPassword` + `register` refactored
  - [ ] 1.4 `notification-service` consumer built
  - [ ] 1.5 Retries + DLT
  - [ ] 1.6 Idempotency drill
- [ ] **Phase 2 — Consumer groups & fan-out**
  - [ ] 2.1 `UserVerifiedEvent` produced
  - [ ] 2.2 Two independent consumers
  - [ ] 2.3 Consumer-group experiment
- [ ] **Phase 3 — Keys, partitions, ordering**
  - [ ] 3.1 Partition key thought experiment
  - [ ] 3.2 `BlogPublishedEvent` produced
  - [ ] 3.3 Toy search-index consumer
  - [ ] 3.4 Schema evolution drill
- [ ] **Phase 4 — Retry & DLT deep dive**
  - [ ] 4.1 Media deletion producer
  - [ ] 4.2 Tiered retry consumer
  - [ ] 4.3 Poison message drill
- [ ] **Phase 5 — Materialized projections ⭐**
  - [ ] 5.1 Design events + owned collection
  - [ ] 5.2 Dual-write phase
  - [ ] 5.3 Backfill
  - [ ] 5.4 Cutover
- [ ] **Phase 6 — Outbox pattern**
  - [ ] 6.1 Break the naive producer
  - [ ] 6.2 Outbox collection
  - [ ] 6.3 Relay poller
  - [ ] 6.4 Idempotent consumer
- [ ] **Phase 7 — Ops & interview polish**
  - [ ] 7.1 Testcontainers integration test
  - [ ] 7.2 Consumer lag monitoring
  - [ ] 7.3 Rebalance experiment
- [ ] **Interview vault complete** — all 20 questions answered in own words, 3+ STAR stories rehearsed out loud

---

## 16. Glossary (bookmark this)

| Term | One-line definition |
|------|--------------------|
| Broker | A single Kafka server. |
| Cluster | A set of brokers acting as one. |
| Topic | A named, ordered log of messages. |
| Partition | One shard of a topic; the unit of parallelism and the unit of ordering. |
| Offset | A message's position in a partition. |
| Producer | Writes messages to a topic. |
| Consumer | Reads messages from a topic and tracks its offset. |
| Consumer group | A set of consumers that split partitions among themselves; each partition is owned by exactly one consumer in the group. |
| Rebalance | The redistribution of partitions when the group membership changes. |
| Offset commit | A consumer telling the broker "I've processed up to here." |
| DLT | Dead Letter Topic — where messages go after retries exhaust. |
| KRaft | Kafka's built-in metadata quorum, replacing Zookeeper since 3.3. |
| `acks` | Producer setting: how many replicas must ack a write before it's "done". |
| Idempotent producer | Producer that dedupes its own retries so the broker doesn't see duplicates. |
| Outbox pattern | Write business row + event row in one DB transaction; a relay ships events to Kafka. Solves dual-write. |
| Materialized view | A local read-optimized projection maintained by consuming events. |
| Schema evolution | Changing an event's payload over time without breaking consumers. |
| Consumer lag | How far behind a consumer group is on a partition, in messages. |
| Compacted topic | A topic where Kafka periodically discards all but the latest message per key. |
| At-least-once | Message is delivered ≥ 1 time; duplicates possible. Kafka default. |

---

*Last updated: Phase 0 draft. As you progress, add your own notes at the end of each phase — this doc is your lab notebook, not a static syllabus.*
