# Notification Engine Client SDK

Client SDK that attaches to a Spring Boot application to send vocabulary, rules, and event-annotated metadata to **acp-server** for agents, and to fire events for `EventProcessorAgent` before they reach the notification engine.

## Enable the SDK

1. Add the client and annotations dependencies:

```xml
<dependency>
  <groupId>com.notify</groupId>
  <artifactId>vocabulary-agent-client</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>com.notify</groupId>
  <artifactId>vocabulary-agent-annotations</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

2. Annotate a `@Configuration` class with `@EnableNotify` and set the scan package:

```java
@Configuration
@EnableNotify(basePackage = "com.myapp")
public class NotifyConfig {}
```

3. Configure in `application.yml`:

```yaml
notify:
  base-package: com.myapp
  acp-server-url: http://localhost:8080
  application-name: my-service
  buffer-batch-size: 100
  buffer-flush-timeout-ms: 5000
  # Optional: Kafka for scheduled events from acp-server
  kafka-enabled: false
  kafka-topic: notify-scheduled-events
  kafka-group: notify-client-group
```

## Annotations

| Annotation | Level | Purpose |
|------------|-------|---------|
| `@EnableNotify` | Class | Enables the SDK; use `basePackage` to scan. |
| `@Event` | Method | Marks a method as an event; intercepted and sent to acp-server. |
| `@Rule` | Method | Rule executor for an event (`name`, `description`, `event`). |
| `@Callback` | Method | Before/after hooks for an event (`event`, `when=BEFORE\|AFTER`). |
| `@Vocabulary` | Field | Vocabulary attribute on `@Model` classes (`name`, `description`). |
| `@Model` | Class | All fields are vocabulary attributes. |
| `@VocabularySupplier` | Method | Supplies event payload for an event (`event`). |
| `@SubjectSupplier` | Method | Supplies list of subjects for an event (`event`). |

## Flow

1. **Bootstrap**: `Bootstrapper` runs `AnnotationProcessor` and `VocabularyManager`, registers the client with acp-server (if `/api/client/register` exists), obtains a token, enqueues vocabulary and rules into `Buffer`, and starts the `Dispatcher` thread.
2. **Events**: `EventListener` (AOP) intercepts `@Event` methods, runs before/after callbacks via `InvokeManager`, builds `EventCaptureDto`, and adds it to `Buffer`. The `Dispatcher` batches and POSTs to acp-server `/api/event`.
3. **Scheduled events**: If `notify.kafka.enabled=true` and Kafka is configured, `NotifyKafkaListener` consumes the configured topic and enqueues `EventCaptureDto` into `Buffer`.

## acp-server endpoints used

- `POST /api/vocabulary` — `List<ClassModel>` (vocabulary from `@Model` / `@Vocabulary`)
- `POST /api/vocabulary/rules/process` — rule map: `eventName`, `ruleName`, `ruleDescription`, `payload`
- `POST /api/event` — `List<EventCapture>` (event captures)
- `POST /api/client/register` — optional; client registration and token
- `POST /api/auth/token/refresh` — optional; token refresh

If `/api/client/register` or `/api/auth/token/refresh` are not implemented, the SDK continues without auth.
