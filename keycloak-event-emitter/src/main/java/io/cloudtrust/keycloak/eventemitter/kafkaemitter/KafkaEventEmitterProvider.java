package io.cloudtrust.keycloak.eventemitter.kafkaemitter;

import io.cloudtrust.keycloak.eventemitter.snowflake.IdGenerator;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;

public class KafkaEventEmitterProvider implements EventListenerProvider {
    private static final Logger logger = Logger.getLogger(KafkaEventEmitterProvider.class);

    private final Producer<String, String> producer;
    private final KeycloakSession keycloakSession;
    private final String eventTopic;
    private final String adminEventTopic;
    private final IdGenerator idGenerator;
    private final LinkedBlockingQueue<EventRecord> pendingEvents;

    private final KafkaEventEmitterState state;
    private final Lock stateLock;

    KafkaEventEmitterProvider(KeycloakSession keycloakSession, Producer<String, String> producer, String eventTopic,
                              String adminEventTopic, IdGenerator idGenerator,
                              LinkedBlockingQueue<EventRecord> pendingEvents, KafkaEventEmitterState state, Lock stateLock) {
        this.keycloakSession = keycloakSession;
        this.producer = producer;
        this.eventTopic = eventTopic;
        this.adminEventTopic = adminEventTopic;
        this.idGenerator = idGenerator;
        this.pendingEvents = pendingEvents;
        this.state = state;
        this.stateLock = stateLock;
    }

    @Override
    public void onEvent(Event event) {
        produceEvent(EventRecord.ofEvent(keycloakSession, idGenerator, event, eventTopic));
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        produceEvent(EventRecord.ofAdminEvent(keycloakSession, idGenerator, adminEvent, adminEventTopic));
    }

    @Override
    public void close() {
    }

    private void send(EventRecord eventRecord) {
        producer.send(eventRecord.getProducerRecord(), (RecordMetadata recordMetadata, Exception e) -> {
            if (e != null) {
                logger.errorf(e, "Failed to send event to Kafka, %s", eventRecord);
            }
        });
    }

    private void enqueue(EventRecord eventRecord, String dropReason) {
        while (!pendingEvents.offer(eventRecord)) {
            EventRecord skippedRecord = pendingEvents.poll();
            if (skippedRecord != null) {
                logger.errorf("Event dropped due to %s, %s, current state: %s", dropReason, skippedRecord, state);
            }
        }
    }

    private void produceEvent(EventRecord eventRecord) {
        if (state.isWorking()) {
            send(eventRecord);
        } else if (producer == null) {
            stateLock.lock();
            enqueue(eventRecord, "full queue and producer is null");
            state.pending();
            stateLock.unlock();
        } else if (state.isStarting() || state.isPending()) {
            stateLock.lock();
            enqueue(eventRecord, "full queue");

            int pendingEventsSize = pendingEvents.size();
            for (int i = 0; i < pendingEventsSize; i++) {
                EventRecord polledRecord = pendingEvents.poll();
                if (polledRecord == null) {
                    break;
                }

                send(polledRecord);
            }
            state.working();
            stateLock.unlock();
        } else {
            logger.errorf("Kafka event emitter is in an indefinite state, event not sent: %s, current state: %s", eventRecord, state);
            throw new IllegalStateException("Kafka event consumer is in an indefinite state");
        }
    }
}
