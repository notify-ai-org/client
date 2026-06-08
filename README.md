<p align="center">
  <span style="font-size: 50px;">⚡</span>
</p>
<h1 align="center" style="border-bottom: none;">
  <span style="font-weight: 800;">Notify<span style="background: linear-gradient(135deg, #f59e0b 0%, #eab308 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent;">.ai</span></span>
</h1>
<p align="center"><b>Client SDK</b> — Lightweight Java Client SDK for Event Interception and Metadata Transmission</p>

---

## 📖 Overview

The **Notify.ai Client SDK** is a lightweight Java library that integrates into Spring Boot applications. Using Aspect-Oriented Programming (AOP) and custom annotations, it intercepts methods, packages parameters as semantic event payloads, and transmits them to the control plane (`acp-server`).

## 🚀 Integration Guide

### 1. Add Dependencies
Add the client SDK and annotations dependencies to your application's `pom.xml`:

```xml
<dependency>
  <groupId>com.notify</groupId>
  <artifactId>vocabulary-agent-client</artifactId>
  <version>1.0.0</version>
</dependency>
<dependency>
  <groupId>com.notify</groupId>
  <artifactId>vocabulary-agent-annotations</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 2. Enable the SDK
Annotate a configuration class with `@EnableNotify` and specify the packages to scan:

```java
@Configuration
@EnableNotify(basePackage = "com.myapp")
public class NotifyConfig {}
```

### 3. Application Properties
Configure connection parameters in your `application.yml` or `application.properties`:

```yaml
notify:
  base-package: com.myapp
  acp-server-url: http://localhost:8080
  application-name: my-service
  buffer-batch-size: 100
  buffer-flush-timeout-ms: 5000
  # Optional: Kafka integration for scheduled events
  kafka-enabled: false
  kafka-topic: notify-scheduled-events
  kafka-group: notify-client-group
```

## 🛠️ Key Annotations

| Annotation | Level | Purpose |
|------------|-------|---------|
| `@EnableNotify` | Class | Enables the SDK; specifies packages to scan. |
| `@Event` | Method | Intercepts execution and forwards payloads to the control plane. |
| `@Rule` | Method | Executes vocabulary rules before/after events. |
| `@Callback` | Method | BEFORE/AFTER hooks running custom logic surrounding event capture. |
| `@Vocabulary` | Field | Declares a field as a vocabulary attribute on model classes. |
| `@Model` | Class | Exposes all fields of the class as vocabulary attributes. |
| `@VocabularySupplier`| Method | Supplies additional context/payload mappings. |
| `@SubjectSupplier` | Method | Maps recipients/subjects for notifications. |

## 🚀 Local Compilation

As a client SDK library, this module cannot be run on its own. It is compiled and installed locally, then imported by your applications.

To compile and package the client SDK locally:
```bash
mvn clean install -pl client
```

For examples of how this SDK is utilized in active projects, refer to the [examples/ecommerce-app](file:///Users/rohannaik/Desktop/notify/examples/ecommerce-app/README.md) and [examples/banking-app](file:///Users/rohannaik/Desktop/notify/examples/banking-app/README.md) directories.

---

## 👥 Developer Contact & Contributing

For questions, issues, or support regarding this module:
- **Lead Developer**: Rohan Naik ([rohan.naik07@github](https://github.com/rohan-naik07))
- **Email**: dev-support@notify.ai

### Contributing

We welcome contributions! Please follow these guidelines:
1. **Fork** the repository and create your branch from `master`.
2. Ensure your changes compile and all tests pass.
3. Follow the project's Java coding standards and naming conventions.
4. Submit a **Pull Request** with a detailed description of your changes.
