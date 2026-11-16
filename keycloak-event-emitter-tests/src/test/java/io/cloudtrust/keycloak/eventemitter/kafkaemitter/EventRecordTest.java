package io.cloudtrust.keycloak.eventemitter.kafkaemitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudtrust.keycloak.eventemitter.snowflake.IdGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Details;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmProvider;
import org.mockito.Mockito;

import java.util.HashMap;

import static org.apache.commons.codec.binary.Base64.isBase64;

class EventRecordTest {
    @Test
    void testEventToString() throws JsonProcessingException {
        var userId = "user-id";
        var topic = "event-topic";
        var details = new HashMap<String, String>();
        details.put(Details.USERNAME, "username");

        var kcSession = Mockito.mock(KeycloakSession.class);
        var idGenerator = Mockito.mock(IdGenerator.class);
        var event = Mockito.mock(Event.class);

        Mockito.when(idGenerator.nextValidId()).thenReturn(12345L);
        Mockito.when(event.getDetails()).thenReturn(details);
        Mockito.when(event.getUserId()).thenReturn(userId);
        Mockito.when(event.getType()).thenReturn(EventType.LOGIN);

        EventRecord eventRecord = EventRecord.ofEvent(kcSession, idGenerator, event, topic);

        Assertions.assertEquals(topic, eventRecord.getProducerRecord().topic());
        Assertions.assertEquals(userId, eventRecord.getProducerRecord().key());
        Assertions.assertTrue(isBase64(eventRecord.getProducerRecord().value()));

        var str = eventRecord.toString().replace("EventRecord[[", "").replace("]]", "");
        var decoded = new ObjectMapper().readValue(str, HashMap.class);
        Assertions.assertEquals(userId, decoded.get("partitionKey"));
        Assertions.assertEquals(topic, decoded.get("topic"));
        Assertions.assertEquals(eventRecord.getProducerRecord().value(), decoded.get("content"));
        Assertions.assertEquals("LOGIN", decoded.get("eventType"));
    }

    @Test
    void testAdminEventToString() throws JsonProcessingException {
        var userId = "user-id";
        var topic = "admin-event-topic";
        var authDetails = new AuthDetails();
        authDetails.setUserId(userId);
        var details = new HashMap<String, String>();
        details.put(Details.USERNAME, "username");

        var kcSession = Mockito.mock(KeycloakSession.class);
        var realmProvider = Mockito.mock(RealmProvider.class);
        var idGenerator = Mockito.mock(IdGenerator.class);
        var adminEvent = Mockito.mock(AdminEvent.class);

        Mockito.when(kcSession.realms()).thenReturn(realmProvider);
        Mockito.when(adminEvent.getDetails()).thenReturn(details);
        Mockito.when(adminEvent.getAuthDetails()).thenReturn(authDetails);
        Mockito.when(adminEvent.getOperationType()).thenReturn(OperationType.CREATE);
        Mockito.when(adminEvent.getResourceType()).thenReturn(ResourceType.USER);

        EventRecord eventRecord = EventRecord.ofAdminEvent(kcSession, idGenerator, adminEvent, topic);
        Assertions.assertTrue(isBase64(eventRecord.getProducerRecord().value()));

        var str = eventRecord.toString().replace("EventRecord[[", "").replace("]]", "");
        var decoded = new ObjectMapper().readValue(str, HashMap.class);
        Assertions.assertEquals(userId, decoded.get("partitionKey"));
        Assertions.assertEquals(topic, decoded.get("topic"));
        Assertions.assertEquals(eventRecord.getProducerRecord().value(), decoded.get("content"));
        Assertions.assertEquals("ADMIN", decoded.get("eventType"));
        Assertions.assertEquals("CREATE", decoded.get("operationType"));
        Assertions.assertEquals("USER", decoded.get("resourceType"));
    }
}
