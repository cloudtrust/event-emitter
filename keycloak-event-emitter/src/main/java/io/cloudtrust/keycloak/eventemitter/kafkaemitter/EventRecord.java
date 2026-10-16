package io.cloudtrust.keycloak.eventemitter.kafkaemitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudtrust.keycloak.eventemitter.CompleteEventUtils;
import io.cloudtrust.keycloak.eventemitter.SerializationUtils;
import io.cloudtrust.keycloak.eventemitter.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedAdminEvent;
import io.cloudtrust.keycloak.eventemitter.customevent.IdentifiedEvent;
import io.cloudtrust.keycloak.eventemitter.snowflake.IdGenerator;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.keycloak.events.Event;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka record along with the event information needed to trace it when it can't be sent.
 */
public class EventRecord {
    private final ProducerRecord<String, String> producerRecord;
    private final Map<String, String> details = new HashMap<>();

    private EventRecord(String topic, String partitionKey, ByteBuffer buffer) {
        this.producerRecord = new ProducerRecord<>(topic, partitionKey, toBase64(buffer));
        this
                .detail("content", producerRecord.value())
                .detail("topic", topic)
                .detail("partitionKey", partitionKey);
    }

    private EventRecord detail(String key, String value) {
        this.details.put(key, value);
        return this;
    }

    public static EventRecord ofEvent(KeycloakSession kcSession, IdGenerator idGenerator, Event event, String topic) {
        CompleteEventUtils.completeEventAttributes(kcSession, event);
        long uid = idGenerator.nextValidId();
        IdentifiedEvent identifiedEvent = new IdentifiedEvent(uid, event);

        var partitionKey = identifiedEvent.getUserId();
        return new EventRecord(topic, partitionKey, SerializationUtils.toFlat(identifiedEvent))
                .detail("realm", identifiedEvent.getRealmId())
                .detail("eventType", identifiedEvent.getType().name());
    }

    public static EventRecord ofAdminEvent(KeycloakSession kcSession, IdGenerator idGenerator, AdminEvent adminEvent, String topic) {
        long uid = idGenerator.nextValidId();
        IdentifiedAdminEvent identifiedAdminEvent = new IdentifiedAdminEvent(uid, adminEvent);
        ExtendedAdminEvent customAdminEvent = CompleteEventUtils.completeAdminEventAttributes(kcSession, identifiedAdminEvent);

        var partitionKey = customAdminEvent.getAuthDetails().getUserId();
        var operationType = customAdminEvent.getOperationType().name();
        var resourceType = customAdminEvent.getResourceTypeAsString();
        return new EventRecord(topic, partitionKey, SerializationUtils.toFlat(customAdminEvent))
                .detail("realm", customAdminEvent.getAuthDetails().getRealmId())
                .detail("eventType", "ADMIN")
                .detail("operationType", operationType)
                .detail("resourceType", resourceType);
    }

    private static String toBase64(ByteBuffer buffer) {
        byte[] b = new byte[buffer.remaining()];
        buffer.get(b);
        return Base64.getEncoder().encodeToString(b);
    }

    public ProducerRecord<String, String> getProducerRecord() {
        return producerRecord;
    }

    @Override
    public String toString() {
        try {
            return "EventRecord[[" + new ObjectMapper().writeValueAsString(details) + "]]";
        } catch (Exception e) {
            return "Failed to serialize EventRecord details";
        }
    }
}
