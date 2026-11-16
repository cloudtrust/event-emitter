# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Keycloak `EventListenerProvider` SPI module (provider id `kafka-event-emitter`) that serializes Keycloak `Event`s and `AdminEvent`s to FlatBuffers, Base64-encodes them, and publishes them to Kafka (separate topics for events and admin events). Java 21. Parent POMs (`kc-cloudtrust-module`, `kc-cloudtrust-testsuite`) and `cloudtrust-common` come from the ELCA Artifactory, so the build needs access to it; the parent version tracks the Keycloak major version.

## Build & test

```bash
mvn compile                       # quick compile check
mvn package                       # builds JAR + TAR.GZ distribution, runs tests
mvn test -pl keycloak-event-emitter-tests -am                            # run tests only
mvn test -pl keycloak-event-emitter-tests -am -Dtest=FlatBufferMapperTest  # single test class
mvn test -pl keycloak-event-emitter-tests -am -Dtest=TypesTest#testEventTypesAreUpToDate -Dsurefire.failIfNoSpecifiedTests=false
```

- `keycloak-event-emitter/` — the module code. SpotBugs, PMD, JaCoCo and OWASP dependency-check plugins are wired in from the parent.
- `keycloak-event-emitter-tests/` — all tests live here (JUnit 5 + Mockito, Kafka `MockProducer`), using the Keycloak test framework via the `kc-cloudtrust-testsuite` parent. This module is never installed/deployed.

## Architecture

Event flow: `KafkaEventEmitterProvider.onEvent(...)` → `CompleteEventUtils` (enriches event with data looked up via the `KeycloakSession`, e.g. usernames, producing `Extended*` types) → wrap in `IdentifiedEvent` / `IdentifiedAdminEvent` with a Snowflake uid from `snowflake.IdGenerator` (for idempotent storage downstream) → `SerializationUtils.toFlat(...)` → Base64 → Kafka `ProducerRecord` keyed by user id.

- **Factory vs provider scope**: `KafkaEventEmitterProviderFactory` is application-scoped and must be thread-safe; it owns the single Kafka `Producer`, the `IdGenerator`, the bounded `LinkedBlockingQueue` of pending records and the `KafkaEventEmitterState`. Providers are request-scoped (single-threaded) but many run concurrently and share the factory's state, guarded by a shared `ReentrantLock`.
- **State machine** (`KafkaEventEmitterState`): `INITIALIZED` (after `init`) → `STARTING` (producer lazily created on first `create`) → `WORKING`; `PENDING` when the producer is unavailable. While not working, records go into the bounded buffer (`bufferCapacity`); when full, the oldest record is dropped. On the next event the buffer is flushed.
- **Configuration**: all keys are read in `KafkaEventEmitterProviderFactory.init` and are mandatory — a missing one throws and Keycloak fails to start. Keys: `bufferCapacity`, `clientId`, `bootstrapServers`, `eventTopic`, `adminEventTopic`, `securityProtocol`, `saslJaasConfig`, `saslOauthbearerTokenEndpointUrl`, `saslMechanism`, `keycloakId`, `datacenterId` (the README's list is slightly out of date).
- **Registration**: via `src/main/resources/META-INF/services/org.keycloak.events.EventListenerProviderFactory`.

## FlatBuffers schema and enum mapping

- Schema: `keycloak-event-emitter/src/main/flatbuffers/events.fbs`. Generated Java classes are **checked in** under `src/main/java/flatbuffers/events/` — do not hand-edit them; regenerate with `flatc --java events.fbs` (from the schema directory, using a `flatc` matching `flatbuffers.version` in the module POM) and place output in `src/main/java/flatbuffers/events`.
- Enum rules (stated in the schema): never remove or reorder values; append new values at the end; for values removed from Keycloak, mark them deprecated rather than deleting. `EventType` and `ResourceType` have an `UNKNOWN` first value as the fallback; `OperationType` does not.
- Keycloak enums are mapped to FlatBuffers enums **by name** via `FlatBufferMapper` (built from the generated `names` arrays). The mapper throws at class init if a Keycloak enum constant has no same-named FlatBuffers value, so a Keycloak upgrade that adds enum values breaks startup until `events.fbs` is updated and regenerated. `TypesTest` verifies the schema enums match the current Keycloak enums exactly.

## Packaging & Keycloak upgrades

`mvn package` produces the JAR plus a `tar.gz` (`keycloak-event-emitter/assembly.xml`) containing the JAR, `install.sh`, `module.conf` and the third-party libs not shipped with Keycloak (`kafka-clients`, `flatbuffers-java`, `jose4j`). When adding a non-Keycloak-provided dependency, add it to the assembly's `dependencySet`; Keycloak-provided ones must stay `provided` scope.

When moving to a new Keycloak version: bump the parent versions in all three POMs, `mvn compile`, update `events.fbs` enums against the Keycloak source and regenerate stubs, then `mvn package` (`TypesTest` will flag enum drift) and check the distribution contents.

Releases use `maven-release-plugin` (tag format `<version>`); commit messages are prefixed with the JIRA key, e.g. `[CLOUDTRUST-1234] ...`.
