package io.cloudtrust.keycloak.kafkaeventemitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.cloudtrust.keycloak.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.customevent.IdentifiedAdminEvent;
import io.cloudtrust.keycloak.customevent.IdentifiedEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SerializationUtilsTest {

    private long UID = 123456789L;

    @Test
    public void testEventToJson() {
        Event event = createEvent();

        try {
            String jsonEvent = SerialisationUtils.toJson(new IdentifiedEvent(UID, event));
            Assertions.assertEquals("{\"id\":null,\"time\":120000,\"type\":\"CLIENT_LOGIN\"," +
                    "\"realmId\":\"realmId\",\"clientId\":\"clientId\",\"userId\":\"userId\"," +
                    "\"sessionId\":\"sessionId\",\"ipAddress\":\"127.0.0.1\",\"error\":\"Error\"," +
                    "\"details\":{\"detailsKey2\":\"detailsValue2\",\"detailsKey1\":\"detailValue1\"},\"uid\":123456789}", jsonEvent);
        } catch (JsonProcessingException e) {
            Assertions.fail();
        }
    }

    @Test
    public void testEventMinimalToJson() {
        Event eventMinimal = createMinimalEvent();
        try {
            String jsonEvent = SerialisationUtils.toJson(new IdentifiedEvent(UID, eventMinimal));
            Assertions.assertEquals("{\"id\":null,\"time\":120000,\"type\":\"CLIENT_LOGIN\"," +
                    "\"realmId\":null,\"clientId\":null,\"userId\":null,\"sessionId\":null," +
                    "\"ipAddress\":null,\"error\":null,\"details\":null,\"uid\":123456789}", jsonEvent);
        } catch (JsonProcessingException e) {
            Assertions.fail();
        }

    }

    @Test
    public void testAdminEventToJson() {
        try {
            String jsonEvent = SerialisationUtils.toJson(createExtendedAdminEvent());
            Assertions.assertEquals("{\"id\":null,\"time\":120000,\"realmId\":\"realmId\",\"resourceType\":\"AUTHORIZATION_RESOURCE\",\"operationType\":\"CREATE\",\"resourcePath\":\"resource/path\",\"representation\":\"representation\",\"error\":\"error\",\"uid\":123456789,\"extAuthDetails\":{\"realmId\":\"authDetails-realmId\",\"clientId\":\"authDetails-clientId\",\"userId\":\"authDetails-userId\",\"ipAddress\":\"authDetails-ipAddress\",\"username\":\"authDetails-username\"},\"details\":{\"user_id\":\"userid\",\"username\":\"username\"}}", jsonEvent);
        } catch (JsonProcessingException e) {
            Assertions.fail();
        }
    }

    @Test
    public void testMinimalAdminEventToJson() {
        try {
            String jsonEvent = SerialisationUtils.toJson(createMinimalExtendedAdminEvent());
            Assertions.assertEquals("{\"id\":null,\"time\":120000,\"realmId\":null,\"resourceType\":null,\"operationType\":null,\"resourcePath\":null,\"representation\":null,\"error\":null,\"uid\":123456789,\"extAuthDetails\":null,\"details\":{}}", jsonEvent);
        } catch (JsonProcessingException e) {
            Assertions.fail();
        }
    }

    @Test
    public void testEventToFlatbuffers() {
        Event event = createEvent();
        ByteBuffer buffer = SerialisationUtils.toFlat(new IdentifiedEvent(UID, event));
        flatbuffers.events.Event deserializedEvent = flatbuffers.events.Event.getRootAsEvent(buffer);
        Assertions.assertTrue(equals(event, deserializedEvent));
        Assertions.assertEquals(UID, deserializedEvent.uid());
    }

    @Test
    public void testMinimalEventToFlatbuffers() {
        Event event = createMinimalEvent();
        ByteBuffer buffer = SerialisationUtils.toFlat(new IdentifiedEvent(UID, event));
        flatbuffers.events.Event deserializedEvent = flatbuffers.events.Event.getRootAsEvent(buffer);
        Assertions.assertTrue(equals(event, deserializedEvent));
        Assertions.assertEquals(UID, deserializedEvent.uid());
    }

    @Test
    public void testAdminEventToFlatbuffers() {
        ExtendedAdminEvent adminEvent = createExtendedAdminEvent();
        ByteBuffer buffer = SerialisationUtils.toFlat(adminEvent);
        flatbuffers.events.AdminEvent deserializedAdminEvent = flatbuffers.events.AdminEvent.getRootAsAdminEvent(buffer);
        Assertions.assertTrue(equals(adminEvent, deserializedAdminEvent));
        Assertions.assertEquals(UID, deserializedAdminEvent.uid());
    }

    @Test
    public void testMinimalAdminEventToFlatbuffers() {
        ExtendedAdminEvent adminEvent = createMinimalExtendedAdminEvent();
        ByteBuffer buffer = SerialisationUtils.toFlat(adminEvent);
        flatbuffers.events.AdminEvent deserializedAdminEvent = flatbuffers.events.AdminEvent.getRootAsAdminEvent(buffer);
        Assertions.assertTrue(equals(adminEvent, deserializedAdminEvent));
        Assertions.assertEquals(UID, deserializedAdminEvent.uid());
    }

    private Event createEvent() {
        Event event = new Event();
        event.setTime(120000);
        event.setType(EventType.CLIENT_LOGIN);
        event.setRealmId("realmId");
        event.setClientId("clientId");
        event.setUserId("userId");
        event.setSessionId("sessionId");
        event.setIpAddress("127.0.0.1");
        event.setError("Error");

        Map<String, String> details = new HashMap<>();
        details.put("detailsKey1", "detailValue1");
        details.put("detailsKey2", "detailsValue2");
        event.setDetails(details);
        return event;
    }

    private Event createMinimalEvent() {
        Event event = new Event();
        event.setTime(120000);
        event.setType(EventType.CLIENT_LOGIN);
        return event;
    }

    private ExtendedAdminEvent createExtendedAdminEvent() {
        IdentifiedAdminEvent iae = new IdentifiedAdminEvent(UID, createAdminEvent());
        ExtendedAdminEvent eae = new ExtendedAdminEvent(iae);
        eae.getAuthDetails().setUsername("authDetails-username");
        eae.getDetails().put("user_id", "userid");
        eae.getDetails().put("username", "username");
        return eae;
    }

    private AdminEvent createAdminEvent() {
        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setTime(120000);
        adminEvent.setRealmId("realmId");
        AuthDetails authDetails = new AuthDetails();
        authDetails.setRealmId("authDetails-realmId");
        authDetails.setClientId("authDetails-clientId");
        authDetails.setIpAddress("authDetails-ipAddress");
        authDetails.setUserId("authDetails-userId");
        adminEvent.setAuthDetails(authDetails);
        adminEvent.setResourceType(ResourceType.AUTHORIZATION_RESOURCE);
        adminEvent.setOperationType(OperationType.CREATE);
        adminEvent.setResourcePath("resource/path");
        adminEvent.setRepresentation("representation");
        adminEvent.setError("error");
        return adminEvent;
    }

    private ExtendedAdminEvent createMinimalExtendedAdminEvent() {
        IdentifiedAdminEvent iae = new IdentifiedAdminEvent(UID, createMinimalAdminEvent());
        return new ExtendedAdminEvent(iae);
    }

    private AdminEvent createMinimalAdminEvent() {
        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setTime(120000);
        return adminEvent;
    }

    private boolean equals(Event event, flatbuffers.events.Event eventFlat) {
        if (event.getTime() != eventFlat.time()) {
            return false;
        }

        if (event.getType().ordinal() != eventFlat.type()) {
            return false;
        }

        if (!Objects.equals(event.getRealmId(), eventFlat.realmId())) {
            return false;
        }

        if (!Objects.equals(event.getClientId(), eventFlat.clientId())) {
            return false;
        }

        if (!Objects.equals(event.getUserId(), eventFlat.userId())) {
            return false;
        }

        if (!Objects.equals(event.getSessionId(), eventFlat.sessionId())) {
            return false;
        }

        if (!Objects.equals(event.getIpAddress(), eventFlat.ipAddress())) {
            return false;
        }

        if (!Objects.equals(event.getError(), eventFlat.error())) {
            return false;
        }

        if (event.getDetails() == null) {
            if (eventFlat.detailsLength() != 0) {
                return false;
            }
        } else if (event.getDetails().size() != eventFlat.detailsLength()) {
            return false;
        }

        return true;
    }

    private boolean equals(ExtendedAdminEvent adminEvent, flatbuffers.events.AdminEvent adminEventFlat) {
        if (adminEvent.getTime() != adminEventFlat.time()) {
            return false;
        }

        if (!Objects.equals(adminEvent.getRealmId(), adminEventFlat.realmId())) {
            return false;
        }

        if (adminEvent.getAuthDetails() == null) {
            if (adminEventFlat.authDetails() != null) {
                return false;
            }
        } else {
            if (!Objects.equals(adminEvent.getAuthDetails().getUserId(), adminEventFlat.authDetails().userId())) {
                return false;
            }
            if (!Objects.equals(adminEvent.getAuthDetails().getUsername(), adminEventFlat.authDetails().username())) {
                return false;
            }
            if (!Objects.equals(adminEvent.getAuthDetails().getClientId(), adminEventFlat.authDetails().clientId())) {
                return false;
            }
            if (!Objects.equals(adminEvent.getAuthDetails().getIpAddress(), adminEventFlat.authDetails().ipAddress())) {
                return false;
            }
            if (!Objects.equals(adminEvent.getAuthDetails().getRealmId(), adminEventFlat.authDetails().realmId())) {
                return false;
            }
        }

        if (adminEvent.getResourceType() == null) {
            if (adminEventFlat.resourceType() != 0) {
                return false;
            }
        } else if (adminEvent.getResourceType().ordinal() != adminEventFlat.resourceType()) {
            return false;
        }

        if (adminEvent.getOperationType() == null) {
            if (adminEventFlat.operationType() != 0) {
                return false;
            }
        } else if (adminEvent.getOperationType().ordinal() != adminEventFlat.operationType()) {
            return false;
        }

        if (!Objects.equals(adminEvent.getResourcePath(), adminEventFlat.resourcePath())) {
            return false;
        }

        if (!Objects.equals(adminEvent.getRepresentation(), adminEventFlat.representation())) {
            return false;
        }


        if (adminEvent.getDetails() == null) {
            if (adminEventFlat.detailsLength() != 0) {
                return false;
            }
        } else if (adminEvent.getDetails().size() != adminEventFlat.detailsLength()) {
            return false;
        }

        if (!Objects.equals(adminEvent.getError(), adminEventFlat.error())) {
            return false;
        }

        return true;
    }

}
