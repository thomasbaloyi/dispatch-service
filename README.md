# Notification Dispatch Service

A REST API that routes notifications to different channels (email, SMS, push) based on user preferences, then publishes dispatch events to Kafka. I'm building this to get solid with functional Scala and the ecosystem—http4s, Cats Effect, Circe, fs2-kafka.

## Why This

I work with Kafka and distributed systems regularly, so I know the event-driven patterns. What I want is to get comfortable with Scala's functional ecosystem without reinventing the wheel on domain design.


## The Stack

| What | Why |
|------|-----|
| http4s + Cats Effect | Solid functional API framework. This is what production Scala uses. |
| Circe | JSON without boilerplate. The codecs are type-safe and compile-time verified. |
| Cats Validated | Validation that accumulates errors instead of failing on the first one. Better UX. |
| Sealed traits + case classes | Makes illegal states impossible. Pattern matching forces you to handle all cases. |
| fs2-kafka | Functional streams for Kafka. Integrates cleanly with Cats Effect. |

## Milestones

(Claude helped me write this part --> I have no idea what most of this is but if it is checked then I have explored it)

### 1. Project setup & hello world
Get the server running, respond to `/health`. Make sure the build works and I understand the basic structure.

**Learn**: http4s routing, Cats Effect resource management, how `HttpApp[F]` works

**Checklist**:
- [ ] build.sbt is set up with http4s, Cats Effect, and basic dependencies
- [ ] Created main.scala with a basic HttpApp
- [ ] Server starts on localhost:8080
- [ ] `curl http://localhost:8080/health` returns 200 OK
- [ ] Understand what Router.of() does and how routes are composed

---

### 2. Model the domain
Define what notifications, channels, and requests look like. Use sealed traits so the compiler makes sure I handle everything.

```scala
sealed trait NotificationType
case object Email extends NotificationType
case object SMS extends NotificationType

sealed trait Channel
case class EmailChannel(address: String) extends Channel
case class SMSChannel(phoneNumber: String) extends Channel
```

**Learn**: ADTs and pattern matching, why sealed traits prevent bugs, thinking in types

**Checklist**:
- [ ] Created domain.scala with NotificationType (Email, SMS, Push)
- [ ] Created Channel trait with case classes for each type
- [ ] Defined NotificationRequest case class
- [ ] Defined DispatchEvent case class (what gets published)
- [ ] Defined User case class (has contact methods, preferences)
- [ ] Wrote a function that pattern matches on all NotificationTypes (forces exhaustiveness)

---

### 3. JSON with Circe
Accept JSON requests, return JSON responses. Use Circe to handle serialization/deserialization without a ton of manual code.

**Learn**: Type classes, how Circe derives codecs, custom codecs when you need them

**Checklist**:
- [ ] Added circe dependency to build.sbt
- [ ] Created codecs.scala with Encoder/Decoder for all domain types
- [ ] Used `derive` for automatic codec generation where possible
- [ ] Wrote custom codec for NotificationType (enum serialization)
- [ ] Test that JSON round-trips correctly (encode then decode = same object)
- [ ] Created a test endpoint that accepts JSON and echoes it back

---

### 4. Validation
Check that incoming requests are valid:
- Does the user exist?
- Do they have at least one way to contact them?
- Is this notification type allowed?

Accumulate all errors and return them together. Don't fail on the first problem.

**Learn**: `Validated` vs `Either`, why accumulating errors matters, `NonEmptyChain`, applicative validation

**Checklist**:
- [ ] Created validation.scala with ValidationError sealed trait
- [ ] Wrote validateUser (check user exists)
- [ ] Wrote validateHasContactMethod (user has email or SMS or push)
- [ ] Wrote validateNotificationType (type is supported)
- [ ] Combined validators using applicative (`Validated` mapN)
- [ ] Test that multiple validation errors are returned together (not just first error)
- [ ] Test error messages are helpful

---

### 5. Routing logic
Pure functions that decide: given this user and this request, which channel(s) do we send to?

Rules might be:
- Prefer email if they have it
- Fall back to SMS if email isn't available
- Some users only want SMS for urgent notifications
- This user doesn't want push notifications

```scala
def route(user: User, request: NotificationRequest): ValidatedNec[Error, List[DispatchEvent]]
```

**Learn**: Keeping logic pure and testable, composition, how functional style forces clarity

**Checklist**:
- [ ] Created routing.scala with route function
- [ ] Implemented basic routing: email first, then SMS, then push
- [ ] Route returns List[DispatchEvent] (possibly multiple if user has multiple contact methods)
- [ ] Handle case where user has no valid channels (return error)
- [ ] Wrote 10+ property-based tests on routing logic
- [ ] Test that route is deterministic (same input = same output)
- [ ] All tests pass without needing Kafka or network

---

### 6. Kafka producer
Publish dispatch events to Kafka using fs2-kafka. This is about how functional Scala integrates with external systems I already know.

**Learn**: fs2 streams and Stream types, Cats Effect integration with external libraries, resource management, composing effects

**Checklist**:
- [ ] Added fs2-kafka to build.sbt
- [ ] Created kafka.scala with KafkaProducer
- [ ] Configured producer (bootstrap servers, serializers, acks setting)
- [ ] Wrote publishEvent(event: DispatchEvent): F[RecordMetadata]
- [ ] Events are serialized to JSON using Circe
- [ ] Started Kafka locally (docker-compose or testcontainers)
- [ ] Can publish an event and consume it with `kafka-console-consumer`
- [ ] Handle producer errors (log and decide: bubble up or queue for retry?)

---

### 7. Wire it together
POST `/notify` should:
1. Parse JSON
2. Validate the request
3. Route to channels
4. Publish to Kafka
5. Return 202 Accepted

Get errors right: 400 for bad input, 500 for system failures.

**Learn**: Request/response lifecycle, error handling at the API boundary

**Checklist**:
- [ ] Created POST /notify endpoint
- [ ] Parse JSON body as NotificationRequest
- [ ] Validate with validation from Milestone 4
- [ ] Route with routing from Milestone 5
- [ ] Publish all dispatch events to Kafka
- [ ] Return 202 Accepted with request ID
- [ ] Return 400 with validation errors (JSON)
- [ ] Return 500 with helpful message if Kafka fails
- [ ] Test with real HTTP requests (curl, test client)
- [ ] Events actually appear in Kafka

---

### 8. Tests
Unit tests on routing logic, property-based tests on validation, integration tests with Testcontainers running real Kafka.

**Learn**: Why pure functions are easy to test, what property-based testing catches, how integration tests validate assumptions

**Checklist**:
- [ ] Added ScalaTest and Testcontainers to build.sbt
- [ ] Routing tests: happy path, no channels, edge cases
- [ ] Validation tests: valid request, missing user, no contact method, all errors returned
- [ ] Property tests: generate random requests, routing is deterministic
- [ ] Integration test: spin up Kafka in container, publish event, verify it was received
- [ ] Integration test: verify serialization round-trips correctly
- [ ] All tests pass locally and in CI
- [ ] Code coverage >80%

---

### 9. Resilience (if I get here)
Handle failures properly:
- Kafka goes down → what happens?
- User service is slow → timeout and circuit break
- Some channels fail but others succeed → what do we report?

Dead letter queue for events that can't be published.

**Learn**: Retry strategies, circuit breakers, partial failures, degradation

**Checklist**:
- [ ] Kafka producer has retry logic (exponential backoff)
- [ ] User service calls have timeouts
- [ ] Write events that fail to a dead letter topic
- [ ] API returns 202 even if some publishes fail (decide the rule)
- [ ] Added structured logging for failures
- [ ] Test Kafka failure scenario: publish still works with retries
- [ ] Test timeout scenario: request doesn't hang forever
- [ ] Graceful shutdown: finish in-flight events before exiting

---

### 10. Deployment (nice to have)
Docker, health checks, config for dev vs prod. Make it actually runnable somewhere.

**Checklist**:
- [ ] Created Dockerfile for the service
- [ ] Created docker-compose.yml (service + Kafka + Zookeeper)
- [ ] `docker-compose up` brings everything up and it works
- [ ] Added OpenAPI/Swagger docs endpoint at /docs
- [ ] Configuration: environment variables for Kafka bootstrap servers, port, etc
- [ ] Health check endpoint at /health (now checks Kafka connectivity too)
- [ ] Basic metrics: request count, latency, event publish time
- [ ] Deployment guide (even if just to Heroku or local)
- [ ] README for local development is accurate

---

## What This Is For

When someone asks "show me you can write Scala," I can point to this and talk about:

- **Functional design**: Pure functions for routing, using types to make invalid states impossible
- **The Scala ecosystem**: How http4s routes work, Cats Effect resource management, Circe codecs, fs2 streams
- **Real systems**: Validation, error handling, async operations, integrating external dependencies
- **Testing strategies**: Unit tests on pure logic, property-based tests, integration tests with testcontainers

It's not learning event systems or distributed thinking—that I already do. It's learning how to think and code that way in Scala.

---

## Start here

```bash
sbt run
# Should start a server on port 8080

sbt test
# Run tests
```
