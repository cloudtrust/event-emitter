package io.cloudtrust.keycloak.eventemitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.cloudtrust.keycloak.customevent.ExtendedAdminEvent;
import io.cloudtrust.keycloak.customevent.ExtendedAuthDetails;
import io.cloudtrust.keycloak.customevent.IdentifiedAdminEvent;
import io.cloudtrust.keycloak.customevent.IdentifiedEvent;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SerializationUtilsTest {

    private final long UID = 123456789L;


    @Test
    public void testContainerToJson() {
        Container c = new Container("Event", "obj");
        try {
            String jsonEvent = SerialisationUtils.toJson(c);
            Assert.assertEquals("{\"type\":\"Event\",\"obj\":\"obj\"}", jsonEvent);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    @Test
    public void testEventToJson() {
        Event event = createEvent();

        try {
            String jsonEvent = SerialisationUtils.toJson(new IdentifiedEvent(UID, event));
            Assert.assertEquals("{\"time\":120000,\"type\":\"CLIENT_LOGIN\"," +
                    "\"realmId\":\"realmId\",\"clientId\":\"clientId\",\"userId\":\"userId\"," +
                    "\"sessionId\":\"sessionId\",\"ipAddress\":\"127.0.0.1\",\"error\":\"Error\"," +
                    "\"details\":{\"detailsKey2\":\"detailsValue2\",\"detailsKey1\":\"detailValue1\"},\"uid\":123456789}", jsonEvent);
        } catch (JsonProcessingException e) {
            Assert.fail();
        }
    }

    @Test
    public void testEventMinimalToJson() {
        Event eventMinimal = createMinimalEvent();
        try {
            String jsonEvent = SerialisationUtils.toJson(new IdentifiedEvent(UID, eventMinimal));
            Assert.assertEquals("{\"time\":120000,\"type\":\"CLIENT_LOGIN\"," +
                    "\"realmId\":null,\"clientId\":null,\"userId\":null,\"sessionId\":null," +
                    "\"ipAddress\":null,\"error\":null,\"details\":null,\"uid\":123456789}", jsonEvent);
        } catch (JsonProcessingException e) {
            Assert.fail();
        }

    }

    @Test
    public void testAdminEventToJson() {
        try {
            String jsonEvent = SerialisationUtils.toJson(createExtendedAdminEvent());
            Assert.assertEquals("{\"time\":120000,\"realmId\":\"realmId\",\"resourceType\":\"AUTHORIZATION_RESOURCE\",\"operationType\":\"CREATE\",\"resourcePath\":\"resource/path\",\"representation\":\"representation\",\"error\":\"error\",\"uid\":123456789,\"extAuthDetails\":{\"realmId\":\"authDetails-realmId\",\"clientId\":\"authDetails-clientId\",\"userId\":\"authDetails-userId\",\"ipAddress\":\"authDetails-ipAddress\",\"username\":\"authDetails-username\"},\"details\":{\"user_id\":\"userid\",\"username\":\"username\"}}", jsonEvent);
        } catch (JsonProcessingException e) {
            Assert.fail();
        }
    }

    @Test
    public void testMinimalAdminEventToJson() {
        try {
            String jsonEvent = SerialisationUtils.toJson(createMinimalExtendedAdminEvent());
            Assert.assertEquals("{\"time\":120000,\"realmId\":null,\"resourceType\":null,\"operationType\":null,\"resourcePath\":null,\"representation\":null,\"error\":null,\"uid\":123456789,\"extAuthDetails\":null,\"details\":{}}", jsonEvent);
        } catch (JsonProcessingException e) {
            Assert.fail();
        }
    }

    @Test
    public void testEventToFlatbuffers() {
        Event event = createEvent();
        ByteBuffer buffer = SerialisationUtils.toFlat(new IdentifiedEvent(UID, event));
        flatbuffers.events.Event deserializedEvent = flatbuffers.events.Event.getRootAsEvent(buffer);
        Assert.assertTrue(equals(event, deserializedEvent));
        Assert.assertEquals(UID, deserializedEvent.uid());
    }

    @Test
    public void testMinimalEventToFlatbuffers() {
        Event event = createMinimalEvent();
        ByteBuffer buffer = SerialisationUtils.toFlat(new IdentifiedEvent(UID, event));
        flatbuffers.events.Event deserializedEvent = flatbuffers.events.Event.getRootAsEvent(buffer);
        Assert.assertTrue(equals(event, deserializedEvent));
        Assert.assertEquals(UID, deserializedEvent.uid());
    }

    @Test
    public void testAdminEventToFlatbuffers() {
        ExtendedAdminEvent adminEvent = createExtendedAdminEvent();
        ByteBuffer buffer = SerialisationUtils.toFlat(adminEvent);
        flatbuffers.events.AdminEvent deserializedAdminEvent = flatbuffers.events.AdminEvent.getRootAsAdminEvent(buffer);
        Assert.assertTrue(equals(adminEvent, deserializedAdminEvent));
        Assert.assertEquals(UID, deserializedAdminEvent.uid());
    }

    @Test
    public void testMinimalAdminEventToFlatbuffers() {
        ExtendedAdminEvent adminEvent = createMinimalExtendedAdminEvent();
        ByteBuffer buffer = SerialisationUtils.toFlat(adminEvent);
        flatbuffers.events.AdminEvent deserializedAdminEvent = flatbuffers.events.AdminEvent.getRootAsAdminEvent(buffer);
        Assert.assertTrue(equals(adminEvent, deserializedAdminEvent));
        Assert.assertEquals(UID, deserializedAdminEvent.uid());
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
        Map<String, String> eventDetails = ObjectUtils.defaultIfNull(event.getDetails(), Collections.emptyMap());
        return new EqualsBuilder()
                .append(event.getTime(), eventFlat.time())
                .append(event.getType().ordinal(), eventFlat.type())
                .append(event.getRealmId(), eventFlat.realmId())
                .append(event.getClientId(), eventFlat.clientId())
                .append(event.getUserId(), eventFlat.userId())
                .append(event.getSessionId(), eventFlat.sessionId())
                .append(event.getIpAddress(), eventFlat.ipAddress())
                .append(event.getError(), eventFlat.error())
                .append(eventDetails.size(), eventFlat.detailsLength())
                .build();
    }

    private boolean equals(ExtendedAdminEvent adminEvent, flatbuffers.events.AdminEvent adminEventFlat) {
        int adminEventResourceType = adminEvent.getResourceType() == null ? 0 : adminEvent.getResourceType().ordinal();
        int adminEventOperationType = adminEvent.getOperationType() == null ? 0 : adminEvent.getOperationType().ordinal();
        int adminEventDetailsSize = adminEvent.getDetails() == null ? 0 : adminEvent.getDetails().size();
        new EqualsBuilder()
                .append(adminEvent.getTime(), adminEventFlat.time())
                .append(adminEvent.getRealmId(), adminEventFlat.realmId())
                .appendSuper(equals(adminEvent.getAuthDetails(), adminEventFlat.authDetails()))
                .append(adminEventResourceType, adminEventFlat.resourceType())
                .append(adminEventOperationType, adminEventFlat.operationType())
                .append(adminEvent.getResourcePath(), adminEventFlat.resourcePath())
                .append(adminEvent.getRepresentation(), adminEventFlat.representation())
                .append(adminEventDetailsSize, adminEventFlat.detailsLength())
                .append(adminEvent.getError(), adminEventFlat.error())
                .build();

        return true;
    }

    private static boolean equals(ExtendedAuthDetails adminEventDetails, flatbuffers.events.AuthDetails adminEventFlatDetails) {
        if (adminEventDetails == null) {
            return adminEventFlatDetails == null;
        }
        return new EqualsBuilder()
                .append(adminEventDetails.getUserId(), adminEventFlatDetails.userId())
                .append(adminEventDetails.getUsername(), adminEventFlatDetails.username())
                .append(adminEventDetails.getClientId(), adminEventFlatDetails.clientId())
                .append(adminEventDetails.getIpAddress(), adminEventFlatDetails.ipAddress())
                .append(adminEventDetails.getRealmId(), adminEventFlatDetails.realmId())
                .build();
    }
}
