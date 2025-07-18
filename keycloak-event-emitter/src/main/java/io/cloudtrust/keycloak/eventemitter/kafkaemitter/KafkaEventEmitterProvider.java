package io.cloudtrust.keycloak.eventemitter.kafkaemitter;

import io.cloudtrust.keycloak.eventemitter.CompleteEventUtils;
import io.cloudtrust.keycloak.eventemitter.SerializationUtils;
import io.cloudtrust.keycloak.eventemitter.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedEvent;
import io.cloudtrust.keycloak.eventemitter.snowflake.IdGenerator;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

public class KafkaEventEmitterProvider implements EventListenerProvider {
    private static final Logger logger = Logger.getLogger(KafkaEventEmitterProvider.class);

    private final Producer<String, String> producer;
    private final KeycloakSession keycloakSession;
    private final String eventTopic;
    private final String adminEventTopic;
    private final IdGenerator idGenerator;
    private final LinkedBlockingQueue<ProducerRecord<String, String>> pendingEvents;

    private final KafkaEventEmitterState state;
    private final Lock stateLock;
    private final CompleteEventUtils completeEventUtils;

    KafkaEventEmitterProvider(KeycloakSession keycloakSession, Producer<String, String> producer, String eventTopic,
                              String adminEventTopic, IdGenerator idGenerator,
                              LinkedBlockingQueue<ProducerRecord<String, String>> pendingEvents, KafkaEventEmitterState state, Lock stateLock) {
        this.keycloakSession = keycloakSession;
        this.producer = producer;
        this.eventTopic = eventTopic;
        this.adminEventTopic = adminEventTopic;
        this.idGenerator = idGenerator;
        this.pendingEvents = pendingEvents;
        this.state = state;
        this.stateLock = stateLock;
        this.completeEventUtils = new CompleteEventUtils(keycloakSession);
    }

    @Override
    public void onEvent(Event event) {
        completeEventUtils.completeEventAttributes(event);
        long uid = idGenerator.nextValidId();
        IdentifiedEvent identifiedEvent = new IdentifiedEvent(uid, event);

        // Flatbuffer serialization
        ByteBuffer buffer = SerializationUtils.toFlat(identifiedEvent);

        produceEvent(buffer, identifiedEvent.getUserId(), eventTopic);
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        long uid = idGenerator.nextValidId();
        IdentifiedAdminEvent identifiedAdminEvent = new IdentifiedAdminEvent(uid, adminEvent);
        ExtendedAdminEvent customAdminEvent = completeEventUtils.completeAdminEventAttributes(identifiedAdminEvent);

        // Flatbuffer serialization
        ByteBuffer buffer = SerializationUtils.toFlat(customAdminEvent);

        produceEvent(buffer, customAdminEvent.getAuthDetails().getUserId(), adminEventTopic);
    }

    @Override
    public void close() {

    }

    private void findUser(String userId, String realmId, Consumer<UserModel> whenUserFound) {
        RealmModel realm = keycloakSession.realms().getRealm(realmId);
        if (realm != null) {
            UserModel user = keycloakSession.users().getUserById(realm, userId);

            if (user != null) {
                whenUserFound.accept(user);
            }
        }
    }

    private void produceEvent(ByteBuffer buffer, String key, String topic) {
        byte[] b = new byte[buffer.remaining()];
        buffer.get(b);

        String eventValue = Base64.getEncoder().encodeToString(b);

        // Event production in Kafka topic
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, eventValue);

        if (state.isWorking()) {
            producer.send(record, (RecordMetadata recordMetadata, Exception e) -> {
                if (e != null) {
                    logger.error(e);
                    logger.error(record);
                }
            });
        } else if (producer == null) {
            stateLock.lock();
            while (!pendingEvents.offer(record)) {
                ProducerRecord<String, String> skippedRecord = pendingEvents.poll();
                if (skippedRecord != null) {
                    logger.errorf("Event dropped due to full queue, event : %s", skippedRecord);
                }
            }
            state.pending();
            stateLock.unlock();
        } else if (state.isStarting() || state.isPending()) {
            stateLock.lock();
            while (!pendingEvents.offer(record)) {
                ProducerRecord<String, String> skippedRecord = pendingEvents.poll();
                if (skippedRecord != null) {
                    logger.errorf("Event dropped due to full queue, event : %s", skippedRecord);
                }
            }

            int pendingEventsSize = pendingEvents.size();
            for (int i = 0; i < pendingEventsSize; i++) {
                ProducerRecord<String, String> polledRecord = pendingEvents.poll();

                if (polledRecord == null) {
                    return;
                }

                producer.send(polledRecord, (RecordMetadata recordMetadata, Exception e) -> {
                    if (e != null) {
                        logger.error(e);
                        logger.error(record);
                    }
                });
            }
            state.working();
            stateLock.unlock();
        } else {
            logger.error(state);
            logger.error(record);
            throw new IllegalStateException("Kafka event consumer is in an indefinite state");
        }
    }
}
