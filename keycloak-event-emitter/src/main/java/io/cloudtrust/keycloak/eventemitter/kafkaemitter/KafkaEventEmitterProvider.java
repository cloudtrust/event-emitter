package io.cloudtrust.keycloak.eventemitter.kafkaemitter;

import io.cloudtrust.keycloak.eventemitter.SerializationUtils;
import io.cloudtrust.keycloak.eventemitter.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.ExtendedAuthDetails;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedEvent;
import io.cloudtrust.keycloak.eventemitter.snowflake.IdGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jboss.logging.Logger;
import org.keycloak.events.Details;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KafkaEventEmitterProvider implements EventListenerProvider {
    private static final Logger logger = Logger.getLogger(KafkaEventEmitterProvider.class);

    private final Producer<String, String> producer;
    private final KeycloakSession keycloakSession;
    private final String eventTopic;
    private final String adminEventTopic;
    private final IdGenerator idGenerator;
    private final LinkedBlockingQueue<ProducerRecord<String, String>> pendingEvents;

    private KafkaEventEmitterState state;
    private Lock stateLock;

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
    }

    @Override
    public void onEvent(Event event) {
        completeEventAttributes(event);
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
        ExtendedAdminEvent customAdminEvent = completeAdminEventAttributes(identifiedAdminEvent);

        // Flatbuffer serialization
        ByteBuffer buffer = SerializationUtils.toFlat(customAdminEvent);

        produceEvent(buffer, customAdminEvent.getAuthDetails().getUserId(), adminEventTopic);
    }

    @Override
    public void close() {

    }

    private void completeEventAttributes(Event event) {
        // add username if missing
        if (event.getDetails() == null) {
            event.setDetails(new HashMap<>());
        }
        String eventUsername = event.getDetails().get(Details.USERNAME);
        if (StringUtils.isNotBlank(event.getUserId()) && StringUtils.isBlank(eventUsername)) {
            findUser(event.getUserId(), event.getRealmId(), u -> event.getDetails().put(Details.USERNAME, u.getUsername()));
        }
    }

    private ExtendedAdminEvent completeAdminEventAttributes(IdentifiedAdminEvent adminEvent) {
        ExtendedAdminEvent extendedAdminEvent = new ExtendedAdminEvent(adminEvent);
        // add always missing agent username
        ExtendedAuthDetails extendedAuthDetails = extendedAdminEvent.getAuthDetails();
        if (StringUtils.isNotBlank(extendedAuthDetails.getUserId())) {
            findUser(extendedAuthDetails.getUserId(), extendedAuthDetails.getRealmId(), u -> extendedAuthDetails.setUsername(u.getUsername()));
        }
        // add username if resource is a user
        String resourcePath = extendedAdminEvent.getResourcePath();
        if (resourcePath != null && resourcePath.contains("users")) {
            // parse userID
            String pattern = ".*users/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$";
            Pattern r = Pattern.compile(pattern);
            Matcher m = r.matcher(resourcePath);
            if (m.matches()) {
                String userId = m.group(1);
                extendedAdminEvent.getDetails().put("target_user_id", userId);
                findUser(userId, adminEvent.getRealmId(), u -> extendedAdminEvent.getDetails().put("target_username", u.getUsername()));
            }
        }

        return extendedAdminEvent;
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
